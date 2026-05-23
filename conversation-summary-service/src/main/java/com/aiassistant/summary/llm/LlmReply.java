package com.aiassistant.summary.llm;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class LlmReply {
    String text;
    String finishReason;
    TokenUsage usage;
}
