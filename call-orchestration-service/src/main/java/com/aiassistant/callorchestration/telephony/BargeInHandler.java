package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;

public interface BargeInHandler {

    void clearCarrierBuffer(CallSession session);

    void cancelTtsGeneration(CallSession session);

    void notifyAiService(CallSession session, String interruptedByText);

    boolean isBotSpeaking(CallSession session);

    boolean tryBargeIn(CallSession session, String sttText,
                       ServiceConfiguration.BargeIn config);
}
