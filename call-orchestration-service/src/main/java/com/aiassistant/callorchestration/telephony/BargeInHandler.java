package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.transcription.TranscriptEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decides when a customer utterance counts as a barge-in on the bot, and
 * cancels in-flight audio/text generation. Provider-agnostic — works with
 * any telephony provider (Exotel, EnableX, Twilio, etc.).
 *
 * <p>One instance per {@link CallSession} — owns the per-call idempotency
 * flag so the burst of interim transcripts inside a single utterance only
 * fires the cancellation once.
 *
 * <h3>Detection — gates (ALL must pass)</h3>
 * <ol>
 *   <li>Bot must be speaking (TTS in-flight or recent activity)</li>
 *   <li>Bot speaking for >= minBotSpeakingMs (grace period)</li>
 *   <li>Text long enough (interims >= minBargeChars, finals always pass)</li>
 *   <li>Confidence above threshold</li>
 *   <li>First hit per utterance (idempotency)</li>
 * </ol>
 *
 * <h3>Actions (synchronous, generic)</h3>
 * <ol>
 *   <li><b>TTS epoch bump.</b> In-flight TTS tasks abort, queued chunks
 *       are dropped, no new audio reaches the caller.</li>
 *   <li><b>{@code BARGE_IN} frame to ai-conversation-service.</b>
 *       Cancels the LLM stream and discards the partial response from
 *       history so the model stays coherent.</li>
 * </ol>
 */
public class BargeInHandler {

    private static final Logger log = LoggerFactory.getLogger(BargeInHandler.class);

    private final int minBargeChars;
    private final double confidenceThreshold;
    /** Bot must have been speaking continuously for at least this many ms
     *  before barge-in is allowed. Prevents bot-audio echo arriving as STT
     *  partials in the first second from self-barging the greeting/reply. */
    private final long minBotSpeakingMs;
    private final AiConversationWsClient aiWsClient;

    /** Per-call idempotency flag. Cleared by {@link #reset()}. */
    private final AtomicBoolean barged = new AtomicBoolean(false);

    public BargeInHandler(int minBargeChars,
                          double confidenceThreshold,
                          long minBotSpeakingMs,
                          AiConversationWsClient aiWsClient) {
        this.minBargeChars = minBargeChars;
        this.confidenceThreshold = confidenceThreshold;
        this.minBotSpeakingMs = minBotSpeakingMs;
        this.aiWsClient = aiWsClient;
    }

    /**
     * Evaluate the three gates and, if all pass, fire the three actions.
     * @return {@code true} if barge-in actually fired (gates passed +
     *         this is the first hit for the current utterance).
     */
    public boolean checkAndBarge(CallSession session, TranscriptEvent event) {
        String text = event.text();
        if (text == null || text.isBlank()) return false;

        // Gate 1: bot must currently be speaking.
        if (!isBotSpeaking(session)) {
            log.info("[barge-in] skip callId={} reason=bot-not-speaking ttsInFlight={} lastTtsMs={} age={}ms text=\"{}\"",
                    session.getCallId(), session.getTtsInFlight().get(), session.getLastTtsActivityMs(),
                    session.getLastTtsActivityMs() > 0 ? System.currentTimeMillis() - session.getLastTtsActivityMs() : -1,
                    truncate(text));
            return false;
        }

        // Gate 1.5: bot must have been speaking for ≥ minBotSpeakingMs.
        // Prevents bot-audio echo (arriving as STT partials in the first
        // second of a sentence) from triggering a self-barge.
        long startedAt = session.getBotSpeakingStartMs();
        if (startedAt > 0) {
            long speakingFor = System.currentTimeMillis() - startedAt;
            if (speakingFor < minBotSpeakingMs) {
                log.info("[barge-in] suppressed callId={} reason=grace-period speakingFor={}ms < {}ms text=\"{}\"",
                        session.getCallId(), speakingFor, minBotSpeakingMs, truncate(text));
                return false;
            }
        }

        // Gate 2: text long enough to be a real interruption (finals always pass).
        boolean longEnough = event.isFinal() || text.trim().length() >= minBargeChars;
        if (!longEnough) return false;

        // Gate 3: confidence above threshold (if reported).
        if (event.confidence() != null && event.confidence() < confidenceThreshold) {
            return false;
        }

        // Idempotency: only the first hit per utterance triggers.
        if (!barged.compareAndSet(false, true)) return false;

        log.info("[barge-in] callId={} interim={} text=\"{}\"",
                session.getCallId(), !event.isFinal(), truncate(text));

        // ── ACTION 1: cancel local TTS (queued + in-flight) ───────────
        cancelLocalTts(session);

        // ── ACTION 2: cancel the in-flight LLM turn on ai-conv ────────
        stopAiResponse(session);

        return true;
    }

    /** Re-arm the handler. Called after an STT final has been forwarded as
     *  a MESSAGE — one utterance has ended, the next is eligible to barge. */
    public void reset() {
        barged.set(false);
    }

    // ── ACTION 2 ──────────────────────────────────────────────────────
    private void stopAiResponse(CallSession session) {
        String conversationId = session.getConversationId();
        if (conversationId == null) return;
        try {
            aiWsClient.sendBargeIn(conversationId);
            log.info("[barge-in] ai-conv BARGE_IN sent callId={} convId={}",
                    session.getCallId(), conversationId);
        } catch (Exception ex) {
            log.warn("[barge-in] ai-conv BARGE_IN failed callId={}: {}",
                    session.getCallId(), ex.getMessage());
        }
    }

    // ── ACTION 3 ──────────────────────────────────────────────────────
    private void cancelLocalTts(CallSession session) {
        long bumped = session.getTtsEpoch().incrementAndGet();
        log.debug("[barge-in] tts epoch bumped callId={} epoch={}",
                session.getCallId(), bumped);
    }

    // ── botSpeaking detection ─────────────────────────────────────────
    /** Carrier playout tail: Twilio + carrier hold up to ~1s of bot audio
     *  after we stop sending chunks. During that window the caller is still
     *  hearing the bot, so an interrupt that lands here must still count
     *  as a barge — not as a fresh turn. */
    private static final long CARRIER_PLAYOUT_TAIL_MS = 1200L;

    /** Bot is speaking iff there is a TTS task actively producing audio,
     *  OR the last outbound chunk reached Twilio within the carrier playout
     *  tail. A simple boolean flag on the session was tried first but it
     *  flickered false between successive per-sentence TTS tasks, which let
     *  interrupts slip through the Gate-1 check. */
    public static boolean isBotSpeaking(CallSession session) {
        if (session.getTtsInFlight().get() > 0) return true;
        long last = session.getLastTtsActivityMs();
        if (last <= 0) return false;
        return (System.currentTimeMillis() - last) < CARRIER_PLAYOUT_TAIL_MS;
    }

    /** TTS pipeline hook — called when a chunk reaches Twilio so the
     *  carrier-tail clock is fresh. The in-flight counter is managed
     *  directly by the TTS task lifecycle (increment on start, decrement
     *  in finally). */
    public static void noteTtsChunkSent(CallSession session) {
        session.setLastTtsActivityMs(System.currentTimeMillis());
    }

    private static String truncate(String s) {
        return s.length() <= 60 ? s : s.substring(0, 60) + "…";
    }
}
