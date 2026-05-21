package com.aiassistant.aiconversation.models.response;

import com.aiassistant.aiconversation.llm.TokenUsage;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SummariseResponse {
    String summary;
    String queryType;
    TokenUsage usage;
    String provider;
}
