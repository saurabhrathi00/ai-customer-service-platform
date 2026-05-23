package com.aiassistant.summary.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class LlmMessage {
    Role role;
    String content;

    public enum Role {
        @JsonProperty("user") USER,
        @JsonProperty("assistant") ASSISTANT;

        @JsonCreator
        public static Role from(String v) {
            return Role.valueOf(v.toUpperCase());
        }
    }
}