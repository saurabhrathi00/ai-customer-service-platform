package com.aiassistant.aiconversation.ws.dto;

import com.aiassistant.aiconversation.llm.TokenUsage;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

public final class OutboundFrames {

    private OutboundFrames() {}

    @Value @Builder @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {
        @Builder.Default WsMessageType type = WsMessageType.RESPONSE;
        String conversationId;
        String replyToMessageId;
        String text;
        TokenUsage usage;
    }

    @Value @Builder @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseDelta {
        @Builder.Default WsMessageType type = WsMessageType.RESPONSE_DELTA;
        String conversationId;
        String replyToMessageId;
        String text;
    }

    @Value @Builder @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseDone {
        @Builder.Default WsMessageType type = WsMessageType.RESPONSE_DONE;
        String conversationId;
        String replyToMessageId;
        String finishReason;
        TokenUsage usage;
    }

    @Value @Builder @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KnowledgeRequest {
        @Builder.Default WsMessageType type = WsMessageType.KNOWLEDGE_REQUEST;
        String conversationId;
        String reason;
    }

    @Value @Builder @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CallbackNeeded {
        @Builder.Default WsMessageType type = WsMessageType.CALLBACK_NEEDED;
        String conversationId;
        String replyToMessageId;
        String text;
        TokenUsage usage;
    }

    @Value @Builder @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Hangup {
        @Builder.Default WsMessageType type = WsMessageType.HANGUP;
        String conversationId;
        String replyToMessageId;
        /** Optional final line spoken to the caller before disconnect. */
        String text;
        /** GOODBYE | UNCLEAR | SILENCE. */
        String reason;
    }

    @Value @Builder @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Error {
        @Builder.Default WsMessageType type = WsMessageType.ERROR;
        String conversationId;
        String code;
        String message;
    }
}