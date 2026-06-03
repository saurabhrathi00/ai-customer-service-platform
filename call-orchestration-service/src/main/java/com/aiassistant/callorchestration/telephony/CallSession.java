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
import java.util.concurrent.atomic.AtomicLong;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession {

    private String callId;
    private String conversationId;
    private String businessId;
    private String businessName;
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

    /**
     * Latched once a HANGUP frame arrives from ai-conv (farewell is being
     * played + Twilio call about to drop). Subsequent inbound STT finals
     * are dropped at the coordinator boundary so we don't queue another
     * LLM turn / another farewell on top of the one already in flight.
     */
    @Builder.Default
    private AtomicBoolean endingCall = new AtomicBoolean(false);

    /** Buffer for the in-flight debounce of STT finals. Each new FINAL
     *  appends + restarts the timer; on timer fire we flush as one
     *  MESSAGE. Synchronised externally by the coordinator. */
    @Builder.Default
    private StringBuilder pendingUtterance = new StringBuilder();
    /** Cancellable timer that ships {@link #pendingUtterance} as a single
     *  MESSAGE after the debounce window elapses with no new STT finals. */
    private volatile java.util.concurrent.ScheduledFuture<?> pendingUtteranceFlush;

    /** Latched true the moment the greeting TTS task finishes streaming
     *  the last chunk to Twilio. STT events arriving before this are
     *  dropped at the source — the caller shouldn't be able to "talk
     *  over" the greeting and have their words queued as a turn. */
    @Builder.Default
    private AtomicBoolean greetingDone = new AtomicBoolean(false);

    /** Provider-specific scratchpad (e.g. Twilio streamSid for outbound frames). */
    @Builder.Default
    private Map<String, Object> providerAttributes = new ConcurrentHashMap<>();

    /** Wall-clock ms when the last TTS task finished sending audio.
     *  Used by the silence watchdog as an anchor — silence is measured
     *  from max(lastTtsActivityMs, lastCallerActivityMs). */
    @Builder.Default
    private volatile long lastTtsActivityMs = 0L;

    /** Wall-clock ms of the last sign of life from the caller — any STT
     *  partial or final. The silence watchdog also bumps this when the
     *  bot finishes speaking, so the "are you there?" timer effectively
     *  starts the moment the caller's turn would naturally begin. */
    @Builder.Default
    private volatile long lastCallerActivityMs = 0L;

    /** Wall-clock ms when the nudge ("are you there?") was played. {@code 0}
     *  means no nudge yet this silence cycle. The watchdog uses this to (a)
     *  not double-nudge and (b) measure post-nudge silence before hangup.
     *  Reset to 0 when the caller resumes speaking. */
    @Builder.Default
    private volatile long silenceNudgedAtMs = 0L;

    /**
     * The {@code messageId} of the most recent customer utterance forwarded to
     * ai-conv. Inbound RESPONSE / RESPONSE_DELTA frames whose
     * {@code replyToMessageId} does not match are dropped — they belong to a
     * turn the user has already barged in on. Volatile so the STT thread and
     * the ai-conv WS thread agree on the current turn without a lock.
     */
    private volatile String activeMessageId;

    /** Wall-clock ms when the current turn's STT FINAL arrived. Used to
     *  compute per-stage latency across the STT→LLM→TTS pipeline. */
    @Builder.Default
    private volatile long turnStartMs = 0L;

    /** Monotonically increasing counter bumped on every barge-in. TTS tasks
     *  snapshot this at start and bail when it changes — no stale audio sent. */
    @Builder.Default
    private AtomicLong ttsEpoch = new AtomicLong(0);

    /** Estimated wall-clock ms when the carrier finishes playing the audio
     *  we've sent so far. Updated on every outbound chunk. Bot is "speaking"
     *  whenever {@code System.currentTimeMillis() < estimatedPlayoutEndMs}. */
    @Builder.Default
    private volatile long estimatedPlayoutEndMs = 0L;

    /** Wall-clock ms when the first TTS chunk of the current turn was sent.
     *  Used for the barge-in grace period — don't interrupt the bot in the
     *  first few hundred ms of speaking. Reset to 0 on barge-in or playout end. */
    @Builder.Default
    private volatile long botSpeakingStartMs = 0L;

    /** Wall-clock ms of the last barge-in event. Used for debouncing
     *  rapid successive barge-in triggers. */
    @Builder.Default
    private volatile long lastBargeInMs = 0L;

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
