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
    private String text;
    private String messageId;
    private String provider;
    private Map<String, Object> metadata;
}