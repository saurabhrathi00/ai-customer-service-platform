package com.aiassistant.aiconversation.ws.dto;

public enum WsMessageType {
    // Inbound (call-orchestration → ai-conversation)
    INIT,
    MESSAGE,
    END,

    // Outbound (ai-conversation → call-orchestration)
    RESPONSE,
    KNOWLEDGE_REQUEST,
    CALLBACK_NEEDED,

    // Bidirectional diagnostic
    ERROR
}