import java.lang.String;
import edu.tufts.hrilab.fol.Symbol;
import edu.tufts.hrilab.fol.Term;
import edu.tufts.hrilab.fol.Predicate;

import edu.tufts.hrilab.llm.Completion;
import edu.tufts.hrilab.llm.Message;
import edu.tufts.hrilab.llm.Chat;
import edu.tufts.hrilab.llm.Prompts;
import edu.tufts.hrilab.llm.Prompt;

// ["Homework 1 LLM Action Script"]

() = llmHw1() {
  op:log("info", "llmHw1");
  String !message = "You are tasked with the generation of Prolog facts and rules: You are shopping robot supposed to shop in a supermarket for tomatoes, milk, and apples.  Apples are in the first isle, tomatoes in the second, and milk in the third.  There is also a cash register at the entrance.  Your actions include going to locations and picking up items from shelves. ONLY generate the appropriate Prolog facts, rules and nothing else";
  Prompt !prompt = op:newObject("edu.tufts.hrilab.llm.Prompt", !message);
  Completion !res = act:chatCompletion(!prompt);
  op:log("info", "got res");
  String !text = op:invokeMethod(!res, "getText");
  java.util.List !codeList = op:invokeMethod(!res, "getCode");
  String !code;

  if (~op:isEmpty(!codeList)) {
    !code = op:invokeMethod(!codeList, "get", 0);
    op:log("info", "Got code");
    op:log("info", !code);
  } else {
    op:log("info", "Did not get code");
    op:log("info", !text);
  }
}