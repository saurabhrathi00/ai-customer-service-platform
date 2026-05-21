package com.aiassistant.aiconversation.llm;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class LlmDelta {
    String text;
    String finishReason;
    TokenUsage usage;
    boolean done;
}
