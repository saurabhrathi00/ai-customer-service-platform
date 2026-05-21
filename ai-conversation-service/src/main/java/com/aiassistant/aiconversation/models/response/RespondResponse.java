package com.aiassistant.aiconversation.models.response;

import com.aiassistant.aiconversation.llm.TokenUsage;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class RespondResponse {
    String text;
    String finishReason;
    TokenUsage usage;
    String provider;
}
