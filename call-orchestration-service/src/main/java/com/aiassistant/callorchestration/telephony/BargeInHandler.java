package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.transcription.TranscriptEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decides when a customer utterance counts as a barge-in on the bot, and
 * fans the cancellation out to all three layers that have audio/text
 * in-flight at that moment.
 *
 * <p>One instance per {@link CallSession} — owns the per-call idempotency
 * flag so the burst of interim transcripts inside a single utterance only
 * fires the cancellation once.
 *
 * <h3>Detection — three gates (ALL must pass)</h3>
 * <ol>
 *   <li><b>Bot must be speaking.</b> If TTS isn't currently playing audio
 *       there is nothing to barge in on — this is just the first utterance
 *       of the next turn.</li>
 *   <li><b>Text long enough.</b> Interim transcripts are checked against a
 *       minimum character count to reject single-phoneme misfires ("ah",
 *       "um", a cough that STT hallucinates a syllable from). Finals
 *       always pass length.</li>
 *   <li><b>Confidence high enough.</b> If STT reports below-threshold
 *       confidence, treat it as noise/echo and ignore.</li>
 * </ol>
 *
 * <h3>Actions — three atomic cancellations</h3>
 * <ol>
 *   <li><b>Twilio "clear" frame.</b> Tells the carrier to drop any bot
 *       audio it has buffered for this {@code streamSid} but not yet
 *       delivered. Without this the caller hears ~1s of stale bot audio
 *       even after we stop synthesising.</li>
 *   <li><b>{@code BARGE_IN} frame to ai-conversation-service.</b>
 *       ai-conv disposes the in-flight Reactor subscription, which
 *       cancels the upstream Gemini HTTP stream so we stop spending
 *       tokens on a question the caller already moved past. It also
 *       drops the partial reply from history so the model's memory stays
 *       coherent.</li>
 *   <li><b>Local TTS cancel.</b> Bumps the session TTS epoch. Pending
 *       ttsExecutor tasks short-circuit before sending audio; the
 *       in-flight TTS streaming loop notices the bump on its next chunk
 *       and aborts the ElevenLabs HTTP read.</li>
 * </ol>
 *
 * <p>After detection fires once, {@link #reset()} re-arms the handler so
 * the next utterance can barge in too. Call sites reset after the STT
 * final has been forwarded as a MESSAGE — that boundary marks one
 * utterance ending and the next being eligible.
 */
public class BargeInHandler {

    private static final Logger log = LoggerFactory.getLogger(BargeInHandler.class);

    private final int minBargeChars;
    private final double confidenceThreshold;
    /** Bot must have been speaking continuously for at least this many ms
     *  before barge-in is allowed. Prevents bot-audio echo arriving as STT
     *  partials in the first second from self-barging the greeting/reply. */
    private final long minBotSpeakingMs;
    private final ObjectMapper objectMapper;
    private final AiConversationWsClient aiWsClient;

    /** Per-call idempotency flag. Cleared by {@link #reset()}. */
    private final AtomicBoolean barged = new AtomicBoolean(false);

    public BargeInHandler(int minBargeChars,
                          double confidenceThreshold,
                          long minBotSpeakingMs,
                          ObjectMapper objectMapper,
                          AiConversationWsClient aiWsClient) {
        this.minBargeChars = minBargeChars;
        this.confidenceThreshold = confidenceThreshold;
        this.minBotSpeakingMs = minBotSpeakingMs;
        this.objectMapper = objectMapper;
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
        if (!isBotSpeaking(session)) return false;

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

        // ── ACTION 1: kill carrier-buffered bot audio ─────────────────
        clearTwilioBuffer(session);

        // ── ACTION 2: cancel the in-flight LLM turn on ai-conv ────────
        stopAiResponse(session);

        // ── ACTION 3: cancel local TTS (queued + in-flight) ───────────
        cancelLocalTts(session);
        return true;
    }

    /** Re-arm the handler. Called after an STT final has been forwarded as
     *  a MESSAGE — one utterance has ended, the next is eligible to barge. */
    public void reset() {
        barged.set(false);
    }

    // ── ACTION 1 ──────────────────────────────────────────────────────
    private void clearTwilioBuffer(CallSession session) {
        WebSocketSession twilioWs = (WebSocketSession) session.getProviderAttributes().get("ws");
        String streamSid = (String) session.getProviderAttributes().get("streamSid");
        if (twilioWs == null || !twilioWs.isOpen() || streamSid == null) return;
        try {
            ObjectNode frame = objectMapper.createObjectNode();
            frame.put("event", "clear");
            frame.put("streamSid", streamSid);
            synchronized (twilioWs) {
                twilioWs.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            }
            log.info("[barge-in] twilio buffer cleared callId={}", session.getCallId());
        } catch (Exception ex) {
            log.warn("[barge-in] twilio clear failed callId={}: {}",
                    session.getCallId(), ex.getMessage());
        }
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
