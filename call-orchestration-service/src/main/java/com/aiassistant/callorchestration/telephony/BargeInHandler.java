package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;

public interface BargeInHandler {

    void clearCarrierBuffer(CallSession session);

    void cancelTtsGeneration(CallSession session);

    void notifyAiService(CallSession session, String interruptedByText);

    boolean isBotSpeaking(CallSession session);

    boolean tryBargeIn(CallSession session, String sttText,
                       ServiceConfiguration.BargeIn config);

    /** Stage 1: pause bot audio on a partial. Returns PAUSE or IMMEDIATE. */
    BargeInAction tryPartialBargeIn(CallSession session, String partialText,
                                    ServiceConfiguration.BargeIn config);

    /** Stage 2: confirm or resume after pause, when final arrives. */
    boolean resolveAfterPause(CallSession session, String finalText,
                              ServiceConfiguration.BargeIn config);

    enum BargeInAction {
        NONE,       // ignore this partial
        PAUSE,      // freeze audio drip, wait for final
        IMMEDIATE   // full barge-in right now (high-confidence partial)
    }
}
