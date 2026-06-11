package com.aiassistant.aiconversation.llm;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class LlmRequest {
    String systemPrompt;
    List<LlmMessage> messages;
    Integer maxOutputTokens;
    Double temperature;
    Boolean cacheSystemPrompt;
    String cachedContentName;
    String modelOverride;
}
