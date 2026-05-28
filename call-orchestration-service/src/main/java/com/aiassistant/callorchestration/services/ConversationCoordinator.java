package com.aiassistant.callorchestration.services;

import com.aiassistant.callorchestration.clients.KnowledgeServiceClient;
import com.aiassistant.callorchestration.clients.ws.AiConversationCallbacks;
import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.CallSessionRegistry;
import de.huxhorn.sulky.ulid.ULID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Glue between the telephony layer and ai-conversation-service.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #onCallStart} — fetches business knowledge, opens WS, sends INIT.</li>
 *   <li>{@link #onCustomerUtterance} — sends MESSAGE; AI reply comes back via {@link CallEventListener#onAiReply}.</li>
 *   <li>{@link #onCallEnd} — sends END, closes WS, removes per-call listener.</li>
 * </ol>
 *
 * <p>The {@link CallEventListener} is supplied by the telephony provider
 * (e.g. {@code TwilioMediaStreamHandler}) which knows how to push the AI
 * reply into TTS for that specific call.
 */
@Service
@RequiredArgsConstructor
public class ConversationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ConversationCoordinator.class);
    private static final ULID ULID_GEN = new ULID();

    private final AiConversationWsClient wsClient;
    private final KnowledgeServiceClient knowledgeServiceClient;
    private final CallSessionRegistry callSessionRegistry;
    private final PostCallOrchestrator postCallOrchestrator;
    @org.springframework.beans.factory.annotation.Qualifier("silenceWatchdogScheduler")
    private final java.util.concurrent.ScheduledExecutorService scheduler;

    /** Debounce window for STT finals. After receiving a FINAL we wait this
     *  many ms; if another FINAL arrives we append and reset the timer.
     *  This smooths over Deepgram's mid-sentence segmentation — natural
     *  pauses inside a single user thought no longer get treated as
     *  separate turns. Tuned higher (1.5s) after callers complained the
     *  bot started replying on top of their mid-sentence pauses. */
    private static final long UTTERANCE_FLUSH_DELAY_MS = 0L;

    private final java.util.concurrent.ConcurrentMap<String, CallEventListener> listeners
            = new java.util.concurrent.ConcurrentHashMap<>();

    public interface CallEventListener {
        /** AI replied with a complete customer-facing text — push to TTS. */
        void onAiReply(String callId, String text);
        /** Streaming partial — incremental text chunk for low-latency TTS. */
        default void onAiReplyChunk(String callId, String deltaText) {}
        /** Streaming terminal — flush any TTS buffer and mark end of turn. */
        default void onAiReplyDone(String callId) {}
        /** ai-conv signalled a transient error / the LLM is unreachable.
         *  The listener should play a canned "trouble" message so the caller
         *  isn't left hanging. */
        default void onAiTransientFailure(String callId) {}
        /** AI cannot answer — telephony should announce callback + hang up. */
        void onCallbackNeeded(String callId);
        /** Conversation is over — telephony synthesizes {@code spokenText}
         *  (if present) and then terminates the call. Reason is GOODBYE /
         *  UNCLEAR / SILENCE. The listener handles speak + hangup as one
         *  atomic operation so the farewell never gets cut off. */
        default void onHangup(String callId, String spokenText, String reason) {}
    }

    public void onCallStart(String callId, CallEventListener listener) {
        CallSession session = callSessionRegistry.get(callId)
                .orElseThrow(() -> new IllegalStateException("No CallSession for callId=" + callId));
        listeners.put(callId, listener);

        // Fire the greeting IMMEDIATELY using only what we already have on
        // the session (businessName came from Twilio custom params at
        // handshake, language is optional). Knowledge fetch + AI WS open
        // used to run synchronously here and could take 10+ seconds, leaving
        // the caller in dead silence before any audio played. They now run
        // on the scheduler thread while the greeting is being TTS'd.
        String greeting = buildGreeting(null, session.getLanguage(), session.getBusinessName());
        session.setGreeting(greeting);
        log.info("[conv] callId={} ASSISTANT (greeting) → \"{}\"", callId, greeting);
        session.getTranscript().add(CallSession.TranscriptEntry.builder()
                .speaker("assistant").text(greeting).timestamp(Instant.now()).build());
        dispatchToListener(callId, l -> l.onAiReply(callId, greeting));

        // Heavy work in the background. By the time the greeting finishes
        // playing (~3s) the WS is typically up and ready for the first
        // user turn. If the user somehow speaks before the WS opens, the
        // utterance flush will retry until the WS is available.
        scheduler.execute(() -> initialiseAiConversation(callId, session));
    }

    private void initialiseAiConversation(String callId, CallSession session) {
        try {
            String knowledge = knowledgeServiceClient.fetchKnowledgeText(session.getBusinessId());
            session.setKnowledgeText(knowledge);
            wsClient.open(buildInit(session), new InboundDispatcher(callId));
            log.info("[init] async knowledge + ai-conv WS ready callId={}", callId);
        } catch (Exception ex) {
            log.error("[init] async knowledge/WS open failed callId={}: {}",
                    callId, ex.getMessage(), ex);
        }
    }

    /**
     * Build the opening line spoken to the caller. Tries to extract the
     * business name from the first header line of the rendered knowledge
     * blob ({@code === BUSINESS PROFILE: <name> ===}); falls back to a
     * generic greeting if the name can't be found.
     */
    /**
     * If the call's language is explicitly known we greet in that language.
     * Otherwise we emit a short bilingual greeting — the customer's first
     * reply sets the language for the rest of the conversation, so we don't
     * accidentally lock them into Hindi when they wanted English (or vice
     * versa). Bilingual is slightly longer but robust without needing a
     * separate language-detection step.
     */
    private String buildGreeting(String knowledge, String language, String sessionBusinessName) {
        // Prefer the name passed in from incoming-call-service via Twilio
        // custom parameters; only fall back to knowledge-blob parsing if it
        // wasn't supplied (e.g. older callers, non-Twilio providers).
        String businessName = (sessionBusinessName != null && !sessionBusinessName.isBlank())
                ? sessionBusinessName
                : extractBusinessName(knowledge);
        String lang = language == null ? "" : language.toLowerCase();
        boolean isEnglish = lang.startsWith("en");
        boolean isHindi   = lang.startsWith("hi");
        if (log.isDebugEnabled()) {
            String firstLine = knowledge == null ? "<null>"
                    : knowledge.substring(0, Math.min(80, knowledge.length()))
                            .replace('\n', ' ');
            log.debug("[greeting] language=\"{}\" isEnglish={} isHindi={} businessName=\"{}\" knowledgeFirstLine=\"{}\"",
                    language, isEnglish, isHindi, businessName, firstLine);
        }

        if (isEnglish) {
            return businessName != null
                    ? "Hello, you've reached " + businessName + ". How can I help you today?"
                    : "Hello, how can I help you today?";
        }
        if (isHindi) {
            return businessName != null
                    ? "Namaste, aap " + businessName + " mein call kar rahe hain. Batayein, main aapki kaise madad kar sakti hoon?"
                    : "Namaste, batayein main aapki kaise madad kar sakti hoon?";
        }
        // Unknown — bilingual.
        return businessName != null
                ? "Hello, namaste! Aap " + businessName + " mein call kar rahe hain — how can I help you today?"
                : "Hello, namaste! How can I help you today?";
    }

    private static String extractBusinessName(String knowledge) {
        if (knowledge == null || knowledge.isBlank()) return null;
        int nl = knowledge.indexOf('\n');
        String first = nl < 0 ? knowledge : knowledge.substring(0, nl);
        String prefix = "=== BUSINESS PROFILE: ";
        String suffix = " ===";
        int s = first.indexOf(prefix);
        if (s < 0) return null;
        int e = first.lastIndexOf(suffix);
        if (e <= s + prefix.length()) return null;
        String name = first.substring(s + prefix.length(), e).trim();
        if (name.isEmpty() || "(unknown)".equalsIgnoreCase(name)) return null;
        return name;
    }

    public void onCustomerUtterance(String callId, String text) {
        onCustomerUtterance(callId, text, true);
    }

    /**
     * @param clear  {@code true} = STT was confident, forward as MESSAGE.
     *               {@code false} = STT below threshold, send UNCLEAR_MESSAGE
     *               so ai-conv can short-circuit to a "please repeat" reply.
     */
    public void onCustomerUtterance(String callId, String text, boolean clear) {
        if (text == null || text.isBlank()) {
            log.debug("[stt] empty final dropped callId={} clear={}", callId, clear);
            return;
        }
        CallSession session = callSessionRegistry.get(callId).orElse(null);
        if (session == null) {
            log.warn("No CallSession for utterance callId={}", callId);
            return;
        }
        // HANGUP has already fired — the farewell is playing and the call
        // is about to drop. Drop any STT finals that arrive in this window
        // so we don't queue another LLM turn / another farewell.
        if (session.getEndingCall().get()) {
            log.debug("[stt] dropped — call ending callId={} text=\"{}\"", callId, text);
            return;
        }

        // Unclear (low-confidence) finals bypass the debounce — they get
        // their own canned "please repeat" reply via ai-conv and shouldn't
        // be merged with prior text.
        if (!clear) {
            String messageId = ULID_GEN.nextULID();
            session.getTranscript().add(CallSession.TranscriptEntry.builder()
                    .speaker("customer").text(text).timestamp(Instant.now()).build());
            session.setActiveMessageId(messageId);
            log.debug("[conv] callId={} CUSTOMER (unclear) → \"{}\"", callId, text);
            log.debug("[ai-req] callId={} msgId={} UNCLEAR_MESSAGE sent to ai-conv", callId, messageId);
            wsClient.sendUnclearMessage(session.getConversationId(), messageId);
            return;
        }

        session.setTurnStartMs(System.currentTimeMillis());
        log.info("[latency] STT-FINAL callId={} text=\"{}\"", callId, text);
        appendAndScheduleFlush(session, text);
    }

    private synchronized void appendAndScheduleFlush(CallSession session, String text) {
        StringBuilder buf = session.getPendingUtterance();
        synchronized (buf) {
            if (buf.length() > 0 && !endsWithSpace(buf)) buf.append(' ');
            buf.append(text);
        }
        log.debug("[stt] FINAL-BUFFERED callId={} bufLen={} chunk=\"{}\"",
                session.getCallId(), buf.length(), text);

        java.util.concurrent.ScheduledFuture<?> prev = session.getPendingUtteranceFlush();
        if (prev != null) prev.cancel(false);
        java.util.concurrent.ScheduledFuture<?> next = scheduler.schedule(
                () -> flushPendingUtterance(session),
                UTTERANCE_FLUSH_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        session.setPendingUtteranceFlush(next);
    }

    private void flushPendingUtterance(CallSession session) {
        String full;
        StringBuilder buf = session.getPendingUtterance();
        synchronized (buf) {
            full = buf.toString().trim();
            buf.setLength(0);
        }
        session.setPendingUtteranceFlush(null);
        if (full.isEmpty()) return;
        if (session.getEndingCall().get()) {
            log.debug("[stt] flush dropped — call ending callId={} text=\"{}\"",
                    session.getCallId(), full);
            return;
        }
        String messageId = ULID_GEN.nextULID();
        session.getTranscript().add(CallSession.TranscriptEntry.builder()
                .speaker("customer").text(full).timestamp(Instant.now()).build());
        session.setActiveMessageId(messageId);
        log.debug("[conv] callId={} CUSTOMER → \"{}\"", session.getCallId(), full);
        long sinceSTT = session.getTurnStartMs() > 0 ? System.currentTimeMillis() - session.getTurnStartMs() : -1;
        log.info("[latency] LLM-SENT callId={} msgId={} sttToLlm={}ms",
                session.getCallId(), messageId, sinceSTT);
        sendUserMessageWithReadinessWait(session.getConversationId(), messageId, full,
                session.getCallId());
    }

    /** Greeting fires before the AI WS open completes, so a user who
     *  speaks during the greeting may get a flush before the WS is ready.
     *  Poll briefly (every 100 ms, up to 5 s) and then give up. The 5 s
     *  ceiling matches the slow-path budget without blocking forever. */
    private void sendUserMessageWithReadinessWait(String conversationId, String messageId,
                                                  String text, String callId) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (wsClient.isOpen(conversationId)) {
                try {
                    wsClient.sendUserMessage(conversationId, messageId, text);
                    return;
                } catch (Exception ex) {
                    log.warn("sendUserMessage failed callId={}: {}", callId, ex.getMessage());
                    return;
                }
            }
            try { Thread.sleep(100); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        log.warn("sendUserMessage gave up — AI WS never opened for callId={} conv={}",
                callId, conversationId);
    }

    private static boolean endsWithSpace(CharSequence cs) {
        if (cs.length() == 0) return true;
        char c = cs.charAt(cs.length() - 1);
        return Character.isWhitespace(c);
    }

    public void onCallEnd(String callId) {
        // listeners.remove() is the idempotency guard — survives the CallSession
        // being removed from the registry by the WS-close path. First caller
        // gets the listener back; the second sees null and exits.
        CallEventListener removed = listeners.remove(callId);
        if (removed == null) {
            log.debug("onCallEnd ignored — already ended callId={}", callId);
            return;
        }
        CallSession session = callSessionRegistry.get(callId).orElse(null);
        String conversationId = session != null ? session.getConversationId() : callId;

        // Cancel any pending STT-debounce flush + drop its buffer. Without
        // this a late timer fires after the WS is gone and we either log
        // a noisy failure or queue a doomed MESSAGE on a closed session.
        if (session != null) {
            java.util.concurrent.ScheduledFuture<?> pending = session.getPendingUtteranceFlush();
            if (pending != null) pending.cancel(false);
            session.setPendingUtteranceFlush(null);
            synchronized (session.getPendingUtterance()) {
                session.getPendingUtterance().setLength(0);
            }
        }

        wsClient.close(conversationId);

        // Normal-end finalisation. ai-conv does not currently send a HISTORY
        // frame back, so we build the history from CallSession.transcript
        // (already populated turn-by-turn during the call) and run the
        // post-call pipeline ourselves. PostCallOrchestrator.finalizeCall is
        // idempotent via session.finalized so a late HISTORY frame or REST
        // /history fallback would be a safe no-op.
        if (session != null) {
            try {
                List<Map<String, String>> history = buildHistoryFromTranscript(session);
                postCallOrchestrator.finalizeCall(session, history);
            } catch (Exception ex) {
                log.error("finalizeCall failed callId={}: {}", callId, ex.getMessage(), ex);
            }
        }
    }

    private static List<Map<String, String>> buildHistoryFromTranscript(CallSession session) {
        if (session.getTranscript() == null || session.getTranscript().isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> out = new java.util.ArrayList<>(session.getTranscript().size());
        for (CallSession.TranscriptEntry e : session.getTranscript()) {
            if (e == null || e.getText() == null || e.getText().isBlank()) continue;
            String role = mapSpeakerToRole(e.getSpeaker());
            out.add(Map.of("role", role, "content", e.getText()));
        }
        return out;
    }

    /** Normalise to LLM-style {@code user} / {@code assistant} roles regardless
     *  of which casing the speaker was written with along the call path. */
    private static String mapSpeakerToRole(String speaker) {
        if (speaker == null) return "user";
        String s = speaker.trim().toLowerCase();
        if (s.equals("assistant") || s.equals("bot") || s.equals("ai")) return "assistant";
        return "user";
    }

    private void dispatchToListener(String callId, Consumer<CallEventListener> action) {
        CallEventListener l = listeners.get(callId);
        if (l == null) {
            log.warn("No CallEventListener for callId={}; dropping event", callId);
            return;
        }
        try { action.accept(l); }
        catch (Exception e) {
            log.warn("CallEventListener failed callId={}: {}", callId, e.getMessage());
        }
    }

    private AiConversationWsClient.InitPayload buildInit(CallSession session) {
        return new AiConversationWsClient.InitPayload(
                session.getConversationId(),
                session.getBusinessId(),
                session.getCallId(),
                session.getKnowledgeText(),
                session.getGreeting(),
                session.getCustomerPhone(),
                session.getLanguage(),
                session.getProvider());
    }

    // ─── Inbound from ai-conversation-service ────────────────────────────

    private final class InboundDispatcher implements AiConversationCallbacks {

        private final String callId;
        /** Accumulator for the current streaming turn — flushed to transcript on RESPONSE_DONE. */
        private final StringBuilder streamingAcc = new StringBuilder();

        InboundDispatcher(String callId) { this.callId = callId; }

        @Override
        public void onResponse(String conversationId, String replyToMessageId, String text) {
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session != null && isStaleTurn(session, replyToMessageId)) {
                log.debug("[ai-resp] STALE-RESPONSE callId={} replyTo={} active={} text=\"{}\"",
                        callId, replyToMessageId, session.getActiveMessageId(), text);
                return;
            }
            log.debug("[ai-resp] RESPONSE callId={} replyTo={} text=\"{}\"",
                    callId, replyToMessageId, text);
            if (session != null) {
                session.getTranscript().add(CallSession.TranscriptEntry.builder()
                        .speaker("assistant").text(text).timestamp(Instant.now()).build());
            }
            dispatch(l -> l.onAiReply(callId, text));
        }

        @Override
        public void onResponseDelta(String conversationId, String replyToMessageId, String deltaText) {
            if (deltaText == null || deltaText.isEmpty()) return;
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session != null && isStaleTurn(session, replyToMessageId)) {
                log.debug("[ai-resp] STALE-DELTA callId={} replyTo={} active={} text=\"{}\"",
                        callId, replyToMessageId, session.getActiveMessageId(), deltaText);
                return;
            }
            log.debug("[ai-resp] DELTA callId={} replyTo={} text=\"{}\"",
                    callId, replyToMessageId, deltaText);
            if (session != null && streamingAcc.isEmpty()) {
                long sinceTurn = session.getTurnStartMs() > 0 ? System.currentTimeMillis() - session.getTurnStartMs() : -1;
                log.info("[latency] LLM-FIRST callId={} sttToFirstToken={}ms", callId, sinceTurn);
            }
            streamingAcc.append(deltaText);
            dispatch(l -> l.onAiReplyChunk(callId, deltaText));
        }

        /** A reply is stale when its {@code replyToMessageId} doesn't match the
         *  session's current active message. {@code null} on the inbound frame
         *  means "no turn correlation" (e.g. the initial greeting path that
         *  goes around ai-conv) — treat as fresh. */
        private boolean isStaleTurn(CallSession session, String replyToMessageId) {
            if (replyToMessageId == null) return false;
            String active = session.getActiveMessageId();
            return active != null && !active.equals(replyToMessageId);
        }

        @Override
        public void onResponseDone(String conversationId, String replyToMessageId, String finishReason) {
            String full = streamingAcc.toString();
            streamingAcc.setLength(0);
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session != null && isStaleTurn(session, replyToMessageId)) {
                log.debug("[ai-resp] STALE-DONE callId={} replyTo={} chars={}",
                        callId, replyToMessageId, full.length());
                return;
            }
            log.debug("[ai-resp] DONE callId={} replyTo={} finishReason={} totalChars={}",
                    callId, replyToMessageId, finishReason, full.length());
            if (!full.isBlank()) {
                if (session != null) {
                    session.getTranscript().add(CallSession.TranscriptEntry.builder()
                            .speaker("assistant").text(full).timestamp(Instant.now()).build());
                }
            }
            dispatch(l -> l.onAiReplyDone(callId));
        }

        @Override
        public void onKnowledgeRequest(String conversationId) {
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session == null) {
                log.warn("Cannot satisfy KNOWLEDGE_REQUEST — no CallSession for callId={}", callId);
                return;
            }
            if (session.getKnowledgeText() == null || session.getKnowledgeText().isBlank()) {
                session.setKnowledgeText(
                        knowledgeServiceClient.fetchKnowledgeText(session.getBusinessId()));
            }
            wsClient.sendInit(buildInit(session));
        }

        @Override
        public void onCallbackNeeded(String conversationId, String replyToMessageId, String spokenText) {
            log.debug("[ai-resp] CALLBACK_NEEDED callId={} replyTo={} spoken=\"{}\"",
                    callId, replyToMessageId, spokenText);
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session != null) {
                session.setCallbackRequested(true);
                if (spokenText != null && !spokenText.isBlank()) {
                    session.getTranscript().add(CallSession.TranscriptEntry.builder()
                            .speaker("assistant").text(spokenText).timestamp(Instant.now()).build());
                }
            }
            if (spokenText != null && !spokenText.isBlank()) {
                dispatch(l -> l.onAiReply(callId, spokenText));
            }
            dispatch(l -> l.onCallbackNeeded(callId));
        }

        @Override
        // Latch the endingCall flag synchronously so any STT final that lands
        // between this method and the actual Twilio teardown is dropped at
        // the onCustomerUtterance boundary. Without this we've seen the model
        // re-emit HANGUP for the second user turn and TTS two farewells.
        public void onHangup(String conversationId, String replyToMessageId,
                             String spokenText, String reason) {
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session != null) {
                // Latch FIRST so any STT final arriving in the next ms gets dropped.
                if (!session.getEndingCall().compareAndSet(false, true)) {
                    log.info("[ai-resp] HANGUP duplicate suppressed callId={}", callId);
                    return;
                }
                // Kill any debounced flush in flight — it would queue a
                // post-HANGUP MESSAGE and trigger another LLM turn just as
                // the farewell starts playing.
                java.util.concurrent.ScheduledFuture<?> pending = session.getPendingUtteranceFlush();
                if (pending != null) pending.cancel(false);
                session.setPendingUtteranceFlush(null);
                synchronized (session.getPendingUtterance()) {
                    session.getPendingUtterance().setLength(0);
                }
                if (spokenText != null && !spokenText.isBlank()) {
                    session.getTranscript().add(CallSession.TranscriptEntry.builder()
                            .speaker("assistant").text(spokenText).timestamp(Instant.now()).build());
                }
            }
            log.info("[ai-resp] HANGUP callId={} reason={} spoken=\"{}\"", callId, reason, spokenText);
            if (spokenText != null && !spokenText.isBlank()) {
                log.info("[conv] callId={} ASSISTANT (farewell) → \"{}\"", callId, spokenText);
            }
            dispatch(l -> l.onHangup(callId, spokenText, reason));
        }

        @Override
        public void onHistory(String conversationId, List<Map<String, String>> history) {
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session == null) {
                log.warn("HISTORY received but no CallSession callId={} conversationId={}",
                        callId, conversationId);
                return;
            }
            postCallOrchestrator.finalizeCall(session, history);
        }

        @Override
        public void onError(String conversationId, String code, String message) {
            log.warn("ai-conv error callId={} code={} msg={}", callId, code, message);
            // Anything LLM-side (transient stream failure, upstream 5xx, etc.)
            // means no DELTA is coming for the in-flight turn. Cue the listener
            // to play a fallback message so the caller hears *something*.
            if (code != null && code.startsWith("LLM_")) {
                dispatch(l -> l.onAiTransientFailure(callId));
            }
        }

        @Override
        public void onClosed(String conversationId, int closeCode, String reason) {
            listeners.remove(callId);
        }

        private void dispatch(Consumer<CallEventListener> action) {
            CallEventListener l = listeners.get(callId);
            if (l == null) {
                log.warn("No CallEventListener for callId={}; dropping event", callId);
                return;
            }
            try { action.accept(l); }
            catch (Exception e) {
                log.warn("CallEventListener failed callId={}: {}", callId, e.getMessage());
            }
        }
    }
}