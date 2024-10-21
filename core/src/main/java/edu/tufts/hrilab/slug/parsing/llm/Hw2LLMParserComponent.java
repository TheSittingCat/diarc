package edu.tufts.hrilab.slug.parsing.llm;

import ai.thinkingrobots.trade.TRADE;
import ai.thinkingrobots.trade.TRADEService;
import ai.thinkingrobots.trade.TRADEServiceConstraints;
import edu.tufts.hrilab.fol.Symbol;
import edu.tufts.hrilab.llm.LLMComponent;


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
        String prompt = "Given a command to the robot shopper that is shopping in a supermarket, respond only with the appropriate command to the robot shopper. For example, if the command is 'go east', the response will be 'intent=instruct,proposition=goEast,type=action,arguments=VAR0'";
        
        try {
            response_llm = TRADE.getAvailableService(new TRADEServiceConstraints().name("chatCompletion").argTypes(String.class)).call(String.class,Input);
        }

        catch (Exception e) {
            log.info(e.getMessage());
            response_llm = Input;
        }
        
        log.info("The answer is: " + response_llm);

        // In the next version, LLM will be used to parse the input and generate the response rather than the following if-else statements. This is just a placeholder
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
        return new AlternateResponse(response, new Symbol("roboshopper"));
    }
}
