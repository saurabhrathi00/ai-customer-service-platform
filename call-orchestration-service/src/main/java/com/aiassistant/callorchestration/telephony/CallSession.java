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

    /** Provider-specific scratchpad (e.g. Twilio streamSid for outbound frames). */
    @Builder.Default
    private Map<String, Object> providerAttributes = new ConcurrentHashMap<>();

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
