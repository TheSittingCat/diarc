package edu.tufts.hrilab.config.hw1;

import edu.tufts.hrilab.diarc.DiarcConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LLMHw1Config extends DiarcConfiguration {
  // for logging
  protected static Logger log = LoggerFactory.getLogger(LLMHw1Config.class);

  @Override
  public void runConfiguration() {
    createInstance(edu.tufts.hrilab.llm.LLMComponent.class, "-endpoint http://vm-llama.eecs.tufts.edu:8080");
    createInstance(edu.tufts.hrilab.action.GoalManagerComponent.class,
        "-goal llmHw1(self) -beliefinitfile demos.pl agents/agents.pl -asl domains/llm/hw1.asl");
  }

  // start the configuration
  public static void main(String[] args) {
    LLMHw1Config config = new LLMHw1Config();
    config.runConfiguration();
  }
}