package com.aiassistant.callorchestration.clients.ws;

/**
 * Mirror of {@code ai-conversation-service}'s {@code WsMessageType}.
 * Kept locally so call-orchestration does not need a shared module.
 */
public enum WsMessageType {
    // Outbound (this service → ai-conversation)
    INIT,
    MESSAGE,
    END,

    // Inbound (ai-conversation → this service)
    RESPONSE,
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