package com.aiassistant.callorchestration.telephony;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession {

    private String callId;
    private String conversationId;
    private String businessId;
    private String customerPhone;
    private String language;
    private String provider;
    private Instant startedAt;

    private String knowledgeText;

    /** Opening line the bot speaks before the customer's first utterance. */
    private String greeting;

    @Builder.Default
    private List<TranscriptEntry> transcript = new ArrayList<>();

    @Builder.Default
    private boolean callbackRequested = false;

    private Integer feedbackScore;

    /**
     * Full conversation history returned by ai-conversation-service at call end
     * (either via the inbound {@code HISTORY} WS frame or via the
     * {@code PUT /api/internal/calls/{conversationId}/history} REST callback).
     * Populated only at call end — empty during the call.
     */
    @Builder.Default
    private List<Map<String, String>> history = new ArrayList<>();

    /**
     * Guard against running the post-call persistence flow twice when both
     * the HISTORY frame and the REST callback fire for the same call.
     */
    @Builder.Default
    private AtomicBoolean finalized = new AtomicBoolean(false);

    /**
     * Guard against running call-end teardown twice when the provider fires
     * both a {@code stop} event and a WS {@code afterConnectionClosed}
     * (typical when the customer hangs up).
     */
    @Builder.Default
    private AtomicBoolean ended = new AtomicBoolean(false);

    /** Provider-specific scratchpad (e.g. Twilio streamSid for outbound frames). */
    @Builder.Default
    private Map<String, Object> providerAttributes = new ConcurrentHashMap<>();

    /** Epoch-ms when the latest customer utterance was sent to ai-conv;
     *  used to measure LLM round-trip and end-to-end perceived latency. */
    @Builder.Default
    private java.util.concurrent.atomic.AtomicLong lastUtteranceSentAtMs =
            new java.util.concurrent.atomic.AtomicLong(0);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranscriptEntry {
        private String speaker;
        private String text;
        private Instant timestamp;
    }
}
