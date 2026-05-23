package com.aiassistant.callorchestration.clients.ws;

/**
 * Mirror of {@code ai-conversation-service}'s {@code WsMessageType}.
 * Kept locally so call-orchestration does not need a shared module.
 */
public enum WsMessageType {
    // Outbound (this service → ai-conversation)
    INIT,
    MESSAGE,
    /** STT confidence below threshold; ai-conv responds with a "please repeat"
     *  message instead of running an LLM turn. */
    UNCLEAR_MESSAGE,
    /** Caller interrupted the bot. Tells ai-conv to cancel the in-flight LLM
     *  turn and drop its partial reply from history. The actual interrupting
     *  utterance arrives in the next MESSAGE frame. */
    BARGE_IN,
    END,

    // Inbound (ai-conversation → this service)
    RESPONSE,
    /** Partial text chunk during a streaming reply. */
    RESPONSE_DELTA,
    /** Terminal frame for a streaming reply — finishReason + usage. */
    RESPONSE_DONE,
    /** ai-conv says the call should be terminated. Reasons: GOODBYE, UNCLEAR, SILENCE. */
    HANGUP,
    KNOWLEDGE_REQUEST,
    CALLBACK_NEEDED,
    /**
     * Sent by ai-conversation-service in response to our {@code END} frame.
     * Carries the full conversation history; consumed by call-orchestration
     * to persist call_logs and fire post-call async tasks.
     */
    HISTORY,

    ERROR
}