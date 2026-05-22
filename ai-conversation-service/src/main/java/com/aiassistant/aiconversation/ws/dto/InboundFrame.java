package com.aiassistant.aiconversation.ws.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundFrame {
    private WsMessageType type;
    private String conversationId;
    private String businessId;
    private String knowledge;
    /** Optional opening line the call-orch already spoke to the caller via TTS.
     *  When present, ai-conv pre-seeds it into history as the first assistant
     *  message so subsequent LLM turns have coherent context. */
    private String greeting;
    private String text;
    private String messageId;
    private String provider;
    private Map<String, Object> metadata;
}