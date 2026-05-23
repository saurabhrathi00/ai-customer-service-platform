package com.aiassistant.aiconversation.ws.dto;

public enum WsMessageType {
    // Inbound (call-orchestration → ai-conversation)
    INIT,
    MESSAGE,
    /** STT confidence was below threshold — caller's audio unclear. ai-conv
     *  should skip the LLM turn and respond with a "please repeat" message
     *  in the caller's language. After N consecutive UNCLEAR_MESSAGEs,
     *  escalate to {@link #CALLBACK_NEEDED}. */
    UNCLEAR_MESSAGE,
    /** Caller interrupted the bot mid-reply — cancel the in-flight LLM turn
     *  immediately and discard whatever partial response has been generated
     *  so the model doesn't think it spoke it. The next {@code MESSAGE} that
     *  arrives carries the interrupting utterance. */
    BARGE_IN,
    END,

    // Outbound (ai-conversation → call-orchestration)
    RESPONSE,
    /** Partial text chunk during a streaming reply. */
    RESPONSE_DELTA,
    /** Terminal frame for a streaming reply — carries finishReason + usage. */
    RESPONSE_DONE,
    /** Conversation is over — call-orch should speak the final line (if any)
     *  and then terminate the telephony call. Reasons: GOODBYE, UNCLEAR, SILENCE. */
    HANGUP,
    KNOWLEDGE_REQUEST,
    CALLBACK_NEEDED,

    // Bidirectional diagnostic
    ERROR
}