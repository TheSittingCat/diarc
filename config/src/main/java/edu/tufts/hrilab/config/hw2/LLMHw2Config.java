package edu.tufts.hrilab.config.hw2;

/* 
Import the following Components: 
SimSpeechRecognitionComponent
LLMComponent
Hw2LLMParserComponent (this is a new one you need to write, see below)
ListenerComponent
PragmaticsComponent
ReferenceResolutionComponent
DialogueComponent
SimpleNLGComponent
GoalManagerComponent
SupermarketComponent
SimSpeechRecognitionComponent
 */

import edu.tufts.hrilab.simspeech.SimSpeechRecognitionComponent;
import edu.tufts.hrilab.llm.LLMComponent;
import edu.tufts.hrilab.slug.parsing.llm.Hw2LLMParserComponent;
import edu.tufts.hrilab.slug.listen.ListenerComponent;
import edu.tufts.hrilab.slug.pragmatics.PragmaticsComponent;
import edu.tufts.hrilab.slug.refResolution.ReferenceResolutionComponent;
import edu.tufts.hrilab.slug.dialogue.DialogueComponent;
import edu.tufts.hrilab.slug.nlg.SimpleNLGComponent;
import edu.tufts.hrilab.nao.NaoComponent;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import edu.tufts.hrilab.slug.parsing.tldl.TLDLParserComponent;
import edu.tufts.hrilab.action.GoalManagerComponent;
import edu.tufts.hrilab.diarc.DiarcConfiguration;
import edu.tufts.hrilab.supermarket.SupermarketComponent;
import edu.tufts.hrilab.simspeech.SimSpeechProductionComponent; // This is used to produce speech to a box rather than an audio device

public class LLMHw2Config extends DiarcConfiguration {
    public boolean simSpeech = true; // Set to true to use gui for speech input, borrowed from TwoNaoDemo
    public boolean mock = true; // Set to true to use mock Nao, borrowed from TwoNaoDemo
    @Override
    public void runConfiguration() {
        if(simSpeech) {
            createInstance(SimSpeechRecognitionComponent.class, "-config hw2/speechinput.simspeech -speaker kaveh -addressee roboshopper");
            createInstance(SimSpeechProductionComponent.class);
        }

        createInstance(ListenerComponent.class);
        //createInstance(TLDLParserComponent.class, "-dict templatedict.dict templatedictLearned.dict"); // This is for the case where LLM is not used, in case of LLM, invoke LLM Component
        createInstance(PragmaticsComponent.class, "-pragrules demos.prag");
        createInstance(ReferenceResolutionComponent.class);
        createInstance(DialogueComponent.class);
        createInstance(SimpleNLGComponent.class);
        createInstance(Hw2LLMParserComponent.class, "-service parseIt"); // This is the new component that we have written

        if(mock) {
            createInstance(SupermarketComponent.class, "-groups agent:roboshopper -agentName roboshopper");
        }
        else {
            createInstance(NaoComponent.class, "-groups agent:dempster -url 192.168.1.7 -unsafe -doNotWakeUp -voice low");
            // This is for the case where we have a real Nao, should be irrelevant for the assignment
        }

        String goal_args = "-beliefinitfile demos.pl domains/supermarket.pl agents/hw2agents.pl "+
        "-asl core.asl vision.asl nao/naodemo.asl dialogue/nlg.asl dialogue/handleSemantics.asl dialogue/nlu.asl domains domain domains/supermarketRefactor.asl " +
        "-goal listen(self)";

        createInstance(GoalManagerComponent.class, goal_args);
}

}