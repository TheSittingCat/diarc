/*
 * Copyright © Thinking Robots, Inc., Tufts University, and others 2024.
 */
package edu.tufts.hrilab.action;

import edu.tufts.hrilab.action.execution.*;
import edu.tufts.hrilab.action.goal.Goal;
import edu.tufts.hrilab.action.goal.GoalStatus;
import edu.tufts.hrilab.action.lock.ActionResourceLock;

import edu.tufts.hrilab.action.priority.PriorityCalculator;
import edu.tufts.hrilab.action.state.StateMachine;
import edu.tufts.hrilab.fol.Symbol;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <code>ActionInterpreter</code> is the primary step execution module for the
 robot.
 */
public class ActionInterpreter implements Callable<ActionStatus> {

  private static Logger log = LoggerFactory.getLogger(ActionInterpreter.class);

  private Lock isRunningLock = new ReentrantLock();
  private volatile boolean isRunning = false;

  /**
   * Root of execution (action)
   */
  private final Context rootStep;

  private ReadWriteLock currentStepLock = new ReentrantReadWriteLock();

  /**
   * Context that is currently being executed by this ActionInterpreter.
   */
  protected Context currentStep;
  private boolean stepInterrupted = false;

  /**
   * Goal that is executed by this ActionInterpreter
   */
  private final Goal goal;

  private double priority = 0;
  private double cost;
  private double benefit;
  private double maxUrgency = 1.0;
  private double minUrgency = 0.0;

  private AtomicBoolean shouldUpdate = new AtomicBoolean(true);

  /**
   * To suspend execution of an ActionInterpreter.
   */
  private volatile boolean suspend = false;
  private Lock suspendLock = new ReentrantLock();
  private Condition suspendCondition = suspendLock.newCondition();
  private AtomicBoolean shouldSuspend = new AtomicBoolean(false);

  /**
   * Flag for canceling this ActionInterpreter.
   */
  private AtomicBoolean shouldCancel = new AtomicBoolean(false);

  /**
   * Class for handling action execution.
   */
  private StepExecution stepExecution;

  /**
   * Listeners. Need to be notified when execution status changes.
   */
  private List<ActionListener> listeners = new ArrayList<>();

  /**
   * The action call stack.
   */
  protected ActionStack callStack;

  /**
   * Handles thread(s) that execute ActionInterpreters.
   */
  private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();
  private final Map<Long, Future<ActionStatus>> asyncFutures = new HashMap<>();

  /**
   * Create a new ActionInterpreter instance to execute a goal.
   * @param goal Goal
   * @param caller Caller context (e.g. root context)
   * @param stateMachine state machine to use for execution
   */
  public static ActionInterpreter createInterpreterFromGoal(Goal goal, Context caller, StateMachine stateMachine) {
    return new ActionInterpreter(goal, caller.addEvent(goal, stateMachine));
  }

  /**
   * Create a new ActionInterpreter instance to execute a goal (with specified executionType).
   * @param goal Goal
   * @param caller Caller context (e.g. root context)
   * @param stateMachine state machine to use for execution
   * @param executionType action execution type
   * @return
   */
  public static ActionInterpreter createInterpreterFromGoal(Goal goal, Context caller, StateMachine stateMachine, ExecutionType executionType) {
    return new ActionInterpreter(goal, caller.addEvent(goal, stateMachine, executionType));
  }

  /**
   * Create new ActionInterpreter instance from an event spec.
   * @param goal Goal
   * @param caller Caller context (e.g. root context)
   * @param eventSpec The EventSpec describing the initial action
   */

  public static ActionInterpreter createInterpreterFromEventSpec(Goal goal, Context caller, EventSpec eventSpec) {
    Context stepContext = caller.addEvent(eventSpec);

    // Bind predicate arguments to context variables (necessary when instantiating through EventSpec)
    goal.bindPredicate(stepContext);

    return new ActionInterpreter(goal, stepContext);
  }

  /**
   * Create new ActionInterpreter instance from an action context.
   * @param step context to be executed (e.g. root context)
   */
  public static ActionInterpreter createInterpreterFromContext(Goal goal, Context step) {
    return new ActionInterpreter(goal, step);
  }

  /**
   * Create a new ActionInterpreter instance from a context that may have been partially executed
   *
   * @param goal the goal which the context tree is working toward
   * @param goalRoot the root context for the goal
   * @return the action new interpreter
   */
  public static ActionInterpreter createInterpreterFromExecutionTree(Goal goal, Context goalRoot) {
    ActionInterpreter ai = new ActionInterpreter(goal, goalRoot);
    Context startStep = ai.createExecutionStack(goalRoot);
    ai.setCurrentStep(startStep);
    goal.setCurrentContext(startStep);
    return ai;
  }

  /**
   * Only ActionInterpreter constructor. All AIs should be constructed through
   * a factory method (e.g., createInterpreterFrom___(...)).
   * @param goal
   * @param rootContext
   */
  protected ActionInterpreter(Goal goal, Context rootContext) {
    this.goal = goal;
    this.goal.setActionInterpreter(this);
    this.rootStep = rootContext;
    this.currentStep = rootStep;
    this.finishInitialization();
  }

  /**
   * Finishes the AI initialization. Should be called at the end of every constructor.
   */
  private void finishInitialization() {
    goal.setRootContext(rootStep);
    goal.setCurrentContext(rootStep);

    //TODO: figure out better way to simulate failed goals
    if (!rootStep.getStatus().isFailure() || !goal.getStatus().isFailure()) {
      callStack = new ActionStack(this);
      if (rootStep instanceof ActionContext) {
        ActionContext action = (ActionContext) rootStep;
        cost = action.getCost();
        benefit = action.getBenefit();
        maxUrgency = action.getMaxUrgency();
        minUrgency = action.getMinUrgency();
      }
      stepExecution = new StepExecution(rootStep, callStack);
    } else {
      goal.setAsTerminated(GoalStatus.FAILED);
      goal.setFailConditions(rootStep.getJustification());
      log.info("Action for goal {} failed with GoalStatus: {}", goal.getPredicate(), goal.getStatus());
    }
  }

  /**
   * Create the execution stack from a context tree which may be partially executed
   *
   * @param context a partially executed context tree used to create execution stack
   * @return the starting context step
   */
  protected Context createExecutionStack(Context context) {
    StateMachine sm = context.getStateMachine();
    ActionStatus status = context.getStatus();
    if (context.isSuccess()) {
      return null;
    } else if (status == ActionStatus.APPROVED || status == ActionStatus.CHECKINGAPPROVAL ||
            status == ActionStatus.INITIALIZED || status == ActionStatus.SATISFYING_PRECONDITIONS ||
            status == ActionStatus.SATISFYING_OBLIGATIONS) {
      context.resetContext();
      return context;
    } else {
      if (context.getChildContexts().isEmpty()) { // primitive action -- in progress or failed
        context.resetContext();
        return context;
      } else {
        if (status == ActionStatus.PROGRESS) { // action script in progress
          callStack.before(context);
          for (Context child : context.getChildContexts().getChildrenContexts()) {
            Context step = createExecutionStack(child);
            if (step != null) {
              return step;
            }
          }
          // action script hasn't been executed (hasn't generated action steps)
          context.setStatus(ActionStatus.APPROVED);
          return context;
        } else if (status.isFailure()) {
          if (context.causedFailure()) {
            context.resetContext();
          } else {
            context.setStatus(ActionStatus.PROGRESS); // this probably won't work, but uncertain
            callStack.before(context);
            for (Context child : context.getChildContexts().getChildrenContexts()) {
              Context step = createExecutionStack(child);
              if (step != null) {
                return step;
              }
            }
          }
          return context;
        }
      }
    }
    // this will be reached if status is cancelled / paused / verifying ... /
    context.resetContext();
    return context;
  }

  /**
   * Execute one cycle of the step interpreter. This is currently equivalent
   * to executing one primitive (i.e., progressing through the script until a
   * primitive is found).
   */
  protected Context runCycle(Context step) {
    log.trace("[runCycle] current step: {}", step.getSignatureInPredicateForm());
    Context nextStep;

    //If we interrupted execution of a primitive on a cancel or suspend call, we don't want to push the same step back
    //  onto the actionStack twice here.
    if (stepInterrupted) {
      stepInterrupted = false;
    } else {
      //  push context step onto action stack
      callStack.before(step);                 // Prepare step execution
    }
    notifyStepExecution(step);
    nextStep = stepExecution.execute(step);
    notifyStepComplete(step);
    if (nextStep == null) {
      log.debug("Next step is null. Current step was {}. Exiting...", step.getSignatureInPredicateForm());
      halt();
    }
    return nextStep;
  }

  /**
   * Execute one cycle of the step interpreter. This is currently equivalent
   * to executing one primitive (i.e., progressing through the script until a
   * primitive is found).
   */
  protected Context runAsyncCycle(Context step) {
    log.trace("[runAsyncCycle] current step: {}", step.getSignatureInPredicateForm());

    callStack.before(step);

    // TODO: it's a bit awkward to create a Goal here, but AIs currently need a goal instance.
    //  Consider getting rid of this requirement?
    Symbol actor = (Symbol) currentStep.getArgumentValue("?actor");
    Goal goal = new Goal(actor, currentStep.getSignatureInPredicateForm());
    ActionInterpreter ai = ActionInterpreter.createInterpreterFromContext(goal, currentStep);
    Future<ActionStatus> asyncFuture = asyncExecutor.submit(ai);
    asyncFutures.put(currentStep.getId(), asyncFuture);

    Context nextStep = callStack.next();
    while (nextStep != null) {
      Context tmpStep = stepExecution.getNextStep(nextStep);
      if (tmpStep != null) {
        nextStep = tmpStep;
        break;
      }
      stepExecution.finishStep(nextStep);
      nextStep = callStack.next();
    }

    if (nextStep == null) {
      if (log.isDebugEnabled()) {
        log.debug("Next step is null. Current step was {}. Exiting...", step.getSignatureInPredicateForm());
      }
      halt();
    }
    return nextStep;
  }

  /**
   * Get the root context executed by this ActionIntepreter.
   * @return root context/step
   */
  public Context getRoot() {
    return rootStep;
  }

  /**
   * Get the cost of the step.
   *
   * @return the cost
   */
  public double getActionCost() {
    return cost;
  }

  /**
   * Get the benefit of the step.
   *
   * @return the benefit
   */
  public double getActionBenefit() {
    return benefit;
  }

  /**
   * Get the maximum time allowed the step.
   *
   * @return the max time
   */
  public long getActionMaxTime() {
    currentStepLock.readLock().lock();
    try {
      return currentStep.getMaxTime();
    } finally {
      currentStepLock.readLock().unlock();
    }
  }

  /**
   * Get the start time for the step.
   *
   * @return the start time
   */
  public long getActionStartTime() {
    currentStepLock.readLock().lock();
    try {
      return currentStep.getStartTime();
    } finally {
      currentStepLock.readLock().unlock();
    }
  }

  /**
   * Get the maximum urgency allowed the step.
   *
   * @return the max urgency
   */
  public double getActionMaxUrg() {
    return maxUrgency;
  }

  /**
   * Get the minimum urgency allowed the step.
   *
   * @return the min urgency
   */
  public double getActionMinUrg() {
    return minUrgency;
  }

  /**
   * Get the step's current priority.
   *
   * @return the priority
   */
  public double getActionPriority() {
    return priority;
  }

  /**
   * Set the step's current priority.
   *
   * @param newpri the new priority
   */
  protected void setActionPriority(double newpri) {
    priority = newpri;
  }

  public Goal getGoal() {
    return goal;
  }

  /**
   * Whether this ActionInterpreter instance is valid (i.e. can run) or not.
   * It all comes down the the status of the current step.
   * @return true if ActionInterpreter is valid and can execute the action.
   */
  // TODO: should this be the root step, if the action is over, current step will
  //       be null,
  protected boolean isValid() {
    currentStepLock.readLock().lock();
    try {
      if (currentStep == null) {
        return false;
      } else {
        return !currentStep.getStatus().isFailure();
      }
    } finally {
      currentStepLock.readLock().unlock();
    }
  }

  /**
   * The main ActionInterpreter run method. After running some checks (locks
   * init and such), it will loop while <code>shouldUpdate</code>. Each loop
   * makes a call to <code>runCycle</code> to execute the behavior for a single
   * loop/cycle.
   */
  @Override
  public ActionStatus call() {
    try {
      // Make sure Action Interpreter is valid and not already running
      isRunningLock.lock();
      try {
        if (this.isValid() && !isRunning) {
          // (Try to) Start goal execution with this interpreter
          if (goal.setAsStarted()) {
            log.debug("Started Action Interpreter for goal: {}. Context id: {}", goal.getPredicate(), rootStep.getId());
          } else {
            log.error("Could not start Action Interpreter (goal refused to start): {}", goal.getPredicate());
            return rootStep.getStatus();
          }
        } else {
          log.error("Could not start Action Interpreter (not valid or already running): {}", goal.getPredicate());
          return rootStep.getStatus();
        }

        isRunning = true;
      } finally {
        isRunningLock.unlock();
      }
      // done ensuring single thread is executing an AI at a time

      notifyStart();
      while (shouldUpdate.get()) {

        //suspend logic
        suspendLock.lock();
        try {
          while (suspend) {
            if (shouldSuspend.get()) {
              suspendSteps();
            }
            try {
              suspendCondition.await();
            } catch (InterruptedException e) {
              log.warn("Interrupted while waiting for suspend condition.", e);
            }
          }
        } finally {
          suspendLock.unlock();
        }

        // cancel logic
        if (shouldCancel.get()) {
          if (currentStep == null) {
            log.warn("[shouldCancel] AI for goal has already terminated: {}", goal.getPredicate());
          } else if (currentStep.isTerminated()) {
            log.error("[shouldCancel] Can't find non-terminated context to cancel: {}", goal.getPredicate());
          } else {
            //Don't think this practically matters and ideally shouldn't reach this case (only possible if there is an
            //  onSuspend behavior defined but no onCancel), but in this case the current step has partially undergone
            //  execution. Appropriately set status to CANCEL.
            if (currentStep.getStatus() == ActionStatus.SUSPEND) {
              currentStep.setStatus(ActionStatus.CANCEL);
            } else {
              // TODO: is PARENT_CANCELLED necessary?
              //Otherwise this context hadn't started any execution, don't want to potentially call any associated onCancel
              // behaviors in this case. Using PARENT_CANCELED status to propagate CANCEL up the tree w/o calling onCancel
              currentStep.setStatus(ActionStatus.PARENT_CANCELED);
            }
            shouldCancel.set(false);
          }
        }

        goal.setCurrentContext(currentStep);
        if (currentStep.isAsynchronous() && currentStep != rootStep) {
          log.trace("about to call runAsyncCycle: {} ...", currentStep.getSignatureInPredicateForm());
          Context nextStep = runAsyncCycle(currentStep);
          setCurrentStep(nextStep);
          log.trace("done with runAsyncCycle.");
        } else {
          log.trace("about to call runCycle: {} ...", currentStep.getSignatureInPredicateForm());
          Context nextStep = runCycle(currentStep);
          setCurrentStep(nextStep);
          log.trace("done with runCycle.");
        }

        // give other threads a chance to execute
        Thread.yield();
      }
      ActionResourceLock.deepReleaseAll(this);
      notifyCompletion();
      isRunning = false;
      return rootStep.getStatus();
    } catch (Exception e) {
      log.error("Exception caught during ActionInterpreter execution.", e);
      return ActionStatus.FAIL;
    }
  }

  protected void setCurrentStep(Context step) {
    currentStepLock.writeLock().lock();
    try {
      currentStep = step;
    } finally {
      currentStepLock.writeLock().unlock();
    }
  }

  /**
   * Suspend the Action Interpreter's script.
   */
  public void suspend() {
    suspendLock.lock();
    try {
      suspend = true;

      if (currentStep instanceof ActionContext && ((ActionContext) currentStep).getDBE().hasOnSuspendEvent()) {
        log.debug("[suspend] interrupting execution of current step: {}", currentStep.getSignatureInPredicateForm());
        stepInterrupted = true;
        suspendSteps();
      } else {
        log.debug("[suspend] Current step not interruptable, setting shouldSuspend to true to cancel before next step. " +
                "Current step: : {}", currentStep.getSignatureInPredicateForm());
        shouldSuspend.set(true);
      }
    } finally {
      suspendLock.unlock();
    }
  }

  private void suspendSteps() {
    if (currentStep == null) {
      log.warn("[suspendSteps] AI for goal has already terminated: {}", goal.getPredicate());
      return;
    } else if (currentStep.isTerminated()) {
      log.warn("[suspendSteps] Current step already terminated, not setting suspend status: {}", goal.getPredicate());
    } else if (currentStep.getStatus() == ActionStatus.INITIALIZED) {
      log.debug("[suspendSteps] current step is INITIALIZED, not setting suspend status: {}", goal.getPredicate());
    } else {
      currentStep.setStatus(ActionStatus.SUSPEND);
    }

    shouldSuspend.set(false);
  }

  // TODO: REDO how CANCEL/SUSPEND/RESUME work !!!

  /**
   * Resume the Action Interpreter's script after suspending.
   *
   * TODO: to resume, the currentStep probably needs to be set to the rootContext so all parent nodes
   *       have a chance to perform onResume actions from the top down
   */
  public void resume() {
    suspendLock.lock();
    try {
      if (suspend) {
        suspend = false;

        if (stepInterrupted) {
          if (currentStep instanceof ActionContext) {
            ((ActionContext) currentStep).handleInterrupt(ActionStatus.RESUME);
          }
          currentStep.setStatus(ActionStatus.INITIALIZED);
        }

        //Propagate status up the tree to be handled per context
        Context caller = currentStep.getParentContext();
        while (caller != null) {
          caller.setStatus(ActionStatus.RESUME);
          caller = caller.getParentContext();
        }

        suspendCondition.signal();
      } else {
        log.warn("[resume] trying to resume an ActionInterpreter that wasn't paused.");
      }
    } finally {
      suspendLock.unlock();
    }
  }

  /**
   * Sets the shouldCancel flag to true, which will cause the main AI loop to
   * set the next step's ActionStatus to CANCEL. This does not explicitly
   * halt the ActionInterpreter, but instead relies on the handling of the
   * CANCEL status by the currently running action. This allows for graceful
   * handling of canceled actions (e.g., via try/catch/finally). This is the
   * only option for external classes.
   */
  public void cancel() {
    //setStatus is only called after the current step completes, so cancelling on a blocking step won't actually
    //  cancel the action until that blocking action terminates unless we interrupt it here
    if (currentStep instanceof ActionContext && ((ActionContext) currentStep).getDBE().hasOnCancelEvent()) {
      log.debug("[cancel] interrupting execution of current step and setting currentStep status to CANCEL. Goal: {} Current step: {}",
              goal.getPredicate(), currentStep.getSignatureInPredicateForm());
      //Setting action status to CANCEL will cause execution to be interrupted automatically
      stepInterrupted = true;
      currentStep.setStatus(ActionStatus.CANCEL);
    } else {
      log.debug("[cancel] Current step not interruptable, setting shouldCancel to true to cancel before next step. Goal: {}", goal.getPredicate());
      shouldCancel.set(true);
    }

    //If canceling from a suspended state, we need to unblock the suspend
    suspendLock.lock();
    try {
      if (suspend) {
        suspend = false;
        suspendCondition.signal();
      }
    } finally {
      suspendLock.unlock();
    }
  }

  /**
   * Halt the Action Interpreter. This is called when the script has no more
   * events.
   */
  private void halt() {
    if (!shouldUpdate.get()) {
      log.error("[halt] halt has already been called: {}", goal.getPredicate());
      return;
    }

    // Stop execution of script (see the while loop inside run()).
    shouldUpdate.set(false);

    // wait for sub-AIs to finish
    for (Long contextId : asyncFutures.keySet()) {
      Future<ActionStatus> currFuture = asyncFutures.get(contextId);
      if (!currFuture.isDone()) {
        log.warn("[halt] waiting for asynchronous context to finish. Goal: {} Context id: {}", goal.getPredicate(), contextId);
        try {
          currFuture.get();
          log.warn("[halt] asynchronous context finished. Goal: {} Context id: {}", goal.getPredicate(), contextId);
        } catch (InterruptedException | ExecutionException e) {
          log.error("[halt] exception waiting for asynchronous context to finish. Goal: {} Context id: {}", goal.getPredicate(), contextId);
        }
      }
    }

    // Update goal end status
    ActionStatus rootActionStatus = rootStep.getStatus();
    if (rootActionStatus == ActionStatus.SUCCESS) {
      goal.setAsTerminated(GoalStatus.SUCCEEDED);
    } else if (rootActionStatus == ActionStatus.CANCEL) {
      goal.setAsTerminated(GoalStatus.CANCELED);
    } else if (rootActionStatus == ActionStatus.SUSPEND) {
      goal.setAsTerminated(GoalStatus.SUSPENDED);
    } else if (rootActionStatus.isFailure()) {
      goal.setAsTerminated(GoalStatus.FAILED);
      goal.setFailConditions(rootStep.getJustification());
    } else {
      log.error("[halt] Could not convert ActionStatus to GoalStatus: {} for action: {}", rootActionStatus, goal.toString());
    }

    if (goal.getStatus().isFailure()) {
      log.info("[halt] goal terminated: {} GoalStatus: {} {}: {}."
              ,goal.getPredicate(), goal.getStatus(), goal.getId(), goal.getFailConditions());
    } else if (log.isDebugEnabled()) {
      log.debug("[halt] goal terminated: {} GoalStatus: {} {}: {}."
              ,goal.getPredicate(), goal.getStatus(), goal.getId(), goal.getFailConditions());
    }
  }

  public void updatePriority(PriorityCalculator calc) {
    priority = calc.calculate(this);
  }

  public void addListener(ActionListener al) {
    listeners.add(al);
  }

  private void notifyStart() {
    for (ActionListener al : listeners) {
      al.actionStarted(this);
    }
  }

  private void notifyCompletion() {
    for (ActionListener al : listeners) {
      al.actionComplete(this);
    }
  }

  private void notifyStepExecution(Context step) {
    for (ActionListener al : listeners) {
      al.stepStarted(step);
    }
  }

  private void notifyStepComplete(Context step) {
    for (ActionListener al : listeners) {
      al.stepComplete(step);
    }
  }

  public Context getCurrentStep() {
    return currentStep;
  }

}
