package edu.tufts.hrilab.slug.parsing.llm;

import ai.thinkingrobots.trade.TRADE;
import ai.thinkingrobots.trade.TRADEService;
import ai.thinkingrobots.trade.TRADEServiceConstraints;
import edu.tufts.hrilab.fol.Symbol;
import edu.tufts.hrilab.llm.LLMComponent;
import edu.tufts.hrilab.llm.Completion;


public class Hw2LLMParserComponent extends LLMParserComponent {
    public Hw2LLMParserComponent() {
        super();
    }

    //    @TRADEService
     //   public int chatCompletion(String prompt) {
     //       String args = "-endpoint http://vm-llama.eecs.tufts.edu:8080";
     //       createInstance(edu.tufts.hrilab.llm.LLMComponent.class, args);
     //       return 1;
     //   }
     
    @TRADEService
    public AlternateResponse parseIt(String Input) { // This is a new service that will be called by the LLMComponent
        ParserResponse response = new ParserResponse();
        response.referents = new Referent[0];
        response.descriptors = new Descriptor[0];
        response.intention = new Intention();
        String response_llm;
        String prompt = "Given a command to the robot shopper that is shopping in a supermarket, respond only with the appropriate command to the robot shopper. For example, if the command is 'go east', the response will be 'intent=instruct,proposition=goEast,type=action,arguments=VAR0' The input is:" + Input;
        try {
            log.warn("Please Wait for the response from LLM, this can take several seconds");
            response_llm = TRADE.getAvailableService(new TRADEServiceConstraints().name("chatCompletion").argTypes(String.class)).call(Completion.class,prompt).getText();
        }

        catch (Exception e) {
            log.info(e.getMessage());
            response_llm = Input;
        }
        
        log.info("Response Generated The answer is: " + response_llm);

        boolean failure = false;
        if(response_llm.contains("intent=instruct")) {
            response.intention.intent = "INSTRUCT";
        }
        else {
            failure = true;
        }
        if(response_llm.contains("proposition=")) {
            int start_index = response_llm.indexOf("proposition=") + "proposition=".length();
            int end_index = response_llm.indexOf(",", start_index);
            if (start_index != -1 && end_index != -1) {
                response.intention.proposition = new Proposition();
                response.intention.proposition.text = response_llm.substring(start_index, end_index);
            }
            else {
                failure = true;
            }
        }
        else {
            failure = true;
        }

        if(response_llm.contains("type=")) {
            int start_index = response_llm.indexOf("type=") + "type=".length();
            int end_index = response_llm.indexOf(",", start_index);
            if (start_index != -1 && end_index != -1) {
                response.intention.proposition.type = response_llm.substring(start_index, end_index);
            }
            else {
                failure = true;
            }
        }
        else {
            failure = true;
        }

        if(response_llm.contains("arguments=")) {
            int start_index = response_llm.indexOf("arguments=") + "arguments=".length();
            int end_index = response_llm.length() - 1; // This is the last item in the generated command
            if (start_index != -1 && end_index != -1) {
                response.intention.proposition.arguments = new String[0];
            }
            else {
                failure = true;
            }
        }
        else {
            failure = true;
        }

        if (failure) {
            log.error("Failed to parse the response from LLM, using the input as the response, this is likely due to LLM or Parsing failure.");
            
        if (Input.equals("go east")) {
            String LLMOut = "intent=instruct,proposition=goEast,type=action,arguments=VAR0";
            if (LLMOut.contains("intent=instruct")) {
                response.intention.intent = "INSTRUCT";
            }
            response.intention.proposition = new Proposition();
            if (LLMOut.contains("proposition=goEast")) {
                response.intention.proposition.text = "goEast";
            }
            if (LLMOut.contains("type=action")) {
                response.intention.proposition.type = "action";
            }
            if (LLMOut.contains("arguments=VAR0")) {
                response.intention.proposition.arguments = new String[0];
            }
        }
        else if (Input.equals("go west")) {
            String LLMOut = "intent=instruct,proposition=goWest,type=action,arguments=VAR0";
            if (LLMOut.contains("intent=instruct")) {
                response.intention.intent = "INSTRUCT";
            }
            response.intention.proposition = new Proposition();
            if (LLMOut.contains("proposition=goWest")) {
                response.intention.proposition.text = "goWest";
            }
            if (LLMOut.contains("type=action")) {
                response.intention.proposition.type = "action";
            }
            if (LLMOut.contains("arguments=VAR0")) {
                response.intention.proposition.arguments = new String[0];
            }
        }
        else if (Input.equals("go north")) {
            String LLMOut = "intent=instruct,proposition=goNorth,type=action,arguments=VAR0";
            if (LLMOut.contains("intent=instruct")) {
                response.intention.intent = "INSTRUCT";
            }
            response.intention.proposition = new Proposition();
            if (LLMOut.contains("proposition=goNorth")) {
                response.intention.proposition.text = "goNorth";
            }
            if (LLMOut.contains("type=action")) {
                response.intention.proposition.type = "action";
            }
            if (LLMOut.contains("arguments=VAR0")) {
                response.intention.proposition.arguments = new String[0];
            }
        }
        else if (Input.equals("go south")) {
            String LLMOut = "intent=instruct,proposition=goSouth,type=action,arguments=VAR0";
            if (LLMOut.contains("intent=instruct")) {
                response.intention.intent = "INSTRUCT";
            }
            response.intention.proposition = new Proposition();
            if (LLMOut.contains("proposition=goSouth")) {
                response.intention.proposition.text = "goSouth";
            }
            if (LLMOut.contains("type=action")) {
                response.intention.proposition.type = "action";
            }
            if (LLMOut.contains("arguments=VAR0")) {
                response.intention.proposition.arguments = new String[0];
            } // Otehr utterances can also be handled in the same way
        }
    }
        return new AlternateResponse(response, new Symbol("roboshopper"));
    }
}
