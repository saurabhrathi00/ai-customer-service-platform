package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;

public interface BargeInHandler {

    void clearCarrierBuffer(CallSession session);

    void cancelTtsGeneration(CallSession session);

    void notifyAiService(CallSession session, String interruptedByText);

    boolean isBotSpeaking(CallSession session);

    BargeInAction onPartial(CallSession session, String partialText,
                            ServiceConfiguration.BargeIn config);

    boolean onFinal(CallSession session, String finalText,
                    ServiceConfiguration.BargeIn config);

    enum BargeInAction {
        NONE,
        PAUSE,
        IMMEDIATE
    }
}
