package com.aiassistant.summary.llm;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TokenUsage {
    long inputTokens;
    long outputTokens;
    long cacheReadInputTokens;
    long cacheCreationInputTokens;

    public static TokenUsage zero() {
        return TokenUsage.builder().build();
    }

    public TokenUsage add(TokenUsage other) {
        if (other == null) return this;
        return TokenUsage.builder()
                .inputTokens(this.inputTokens + other.inputTokens)
                .outputTokens(this.outputTokens + other.outputTokens)
                .cacheReadInputTokens(this.cacheReadInputTokens + other.cacheReadInputTokens)
                .cacheCreationInputTokens(this.cacheCreationInputTokens + other.cacheCreationInputTokens)
                .build();
    }
}