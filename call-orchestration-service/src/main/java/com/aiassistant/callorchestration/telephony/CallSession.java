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
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * Monotonically increasing TTS epoch. Bumped on every barge-in. TTS tasks
     * capture the epoch on submit and abort if it advances before they finish —
     * keeps stale bot audio from playing after the user has started talking.
     */
    @Builder.Default
    private AtomicLong ttsEpoch = new AtomicLong(0);

    /**
     * Number of TTS tasks currently producing audio for this call. Incremented
     * when a task starts streaming, decremented in its finally block. The
     * barge-in handler treats {@code ttsInFlight > 0} (or recent activity
     * within the carrier playout tail) as "bot is speaking".
     */
    @Builder.Default
    private AtomicInteger ttsInFlight = new AtomicInteger(0);

    /** Wall-clock ms of the last outbound TTS chunk reaching Twilio. Used by
     *  the barge-in handler to keep "bot speaking" true during the carrier
     *  playout tail (~1s after we stop synthesising) so the caller's
     *  interrupt during that window still counts as a barge. */
    @Builder.Default
    private volatile long lastTtsActivityMs = 0L;

    /** Wall-clock ms when the CURRENT continuous bot-speaking burst started.
     *  Used by the barge-in handler to enforce a grace period at the start
     *  of each utterance — without it, bot-audio echo arriving as STT
     *  partials in the first second triggers a self-barge that cuts the
     *  bot's own greeting / sentence off. Reset to "now" on a 0→1
     *  ttsInFlight transition if the previous burst ended more than a short
     *  inter-sentence gap ago; left alone for back-to-back sentences. */
    @Builder.Default
    private volatile long botSpeakingStartMs = 0L;

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
