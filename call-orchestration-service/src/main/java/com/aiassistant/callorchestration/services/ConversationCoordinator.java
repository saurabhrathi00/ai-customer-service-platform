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

    private final java.util.concurrent.ConcurrentMap<String, CallEventListener> listeners
            = new java.util.concurrent.ConcurrentHashMap<>();

    // ─── Silence detection ──────────────────────────────────────────────
    // Polls active CallSessions every {@value #SILENCE_CHECK_INTERVAL_MS}ms;
    // if the caller has been silent and the bot is not currently speaking,
    // sends a SILENCE_PROMPT to ai-conv which decides whether to re-engage
    // or hang up.
    private static final long SILENCE_THRESHOLD_MS = 4_000;
    private static final long SILENCE_CHECK_INTERVAL_MS = 1_000;

    private final java.util.concurrent.ScheduledExecutorService silenceScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "silence-detector");
                t.setDaemon(true);
                return t;
            });
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.ScheduledFuture<?>>
            silenceTasks = new java.util.concurrent.ConcurrentHashMap<>();

    public interface CallEventListener {
        /** AI replied with a complete customer-facing text — push to TTS. */
        void onAiReply(String callId, String text);
        /** Streaming partial — incremental text chunk for low-latency TTS. */
        default void onAiReplyChunk(String callId, String deltaText) {}
        /** Streaming terminal — flush any TTS buffer and mark end of turn. */
        default void onAiReplyDone(String callId) {}
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

        String knowledge = knowledgeServiceClient.fetchKnowledgeText(session.getBusinessId());
        session.setKnowledgeText(knowledge);

        String greeting = buildGreeting(knowledge, session.getLanguage());
        session.setGreeting(greeting);

        listeners.put(callId, listener);
        wsClient.open(buildInit(session), new InboundDispatcher(callId));

        // Initialize silence clock to "now" so we don't fire prompts during
        // the bot's opening greeting — botSpeaking will be true while the
        // greeting plays, so the tick will skip naturally.
        long now = System.currentTimeMillis();
        session.getProviderAttributes().putIfAbsent("sttFinalAtMs", now);
        session.getProviderAttributes().put("lastSilencePromptAtMs", now);
        java.util.concurrent.ScheduledFuture<?> sf = silenceScheduler.scheduleAtFixedRate(
                () -> tickSilence(callId),
                SILENCE_CHECK_INTERVAL_MS * 2, SILENCE_CHECK_INTERVAL_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        silenceTasks.put(callId, sf);

        // Bot speaks first — no LLM round-trip on the opening turn. The same
        // greeting is also pre-seeded in ai-conv history via the INIT frame,
        // so subsequent turns stay context-coherent.
        session.getTranscript().add(CallSession.TranscriptEntry.builder()
                .speaker("assistant").text(greeting).timestamp(Instant.now()).build());
        dispatchToListener(callId, l -> l.onAiReply(callId, greeting));

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
    private String buildGreeting(String knowledge, String language) {
        String businessName = extractBusinessName(knowledge);
        String lang = language == null ? "" : language.toLowerCase();
        boolean isEnglish = lang.startsWith("en");
        boolean isHindi   = lang.startsWith("hi");

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
        if (text == null || text.isBlank()) return;
        String messageId = ULID_GEN.nextULID();
        CallSession session = callSessionRegistry.get(callId).orElse(null);
        if (session == null) {
            log.warn("No CallSession for utterance callId={}", callId);
            return;
        }
        session.getTranscript().add(CallSession.TranscriptEntry.builder()
                .speaker("customer").text(text).timestamp(Instant.now()).build());
        session.getLastUtteranceSentAtMs().set(System.currentTimeMillis());
        if (clear) {
            wsClient.sendUserMessage(session.getConversationId(), messageId, text);
        } else {
            log.info("[unclear] callId={} conversationId={} sttText=\"{}\" — sending UNCLEAR_MESSAGE",
                    callId, session.getConversationId(), text);
            wsClient.sendUnclearMessage(session.getConversationId(), messageId);
        }
    }

    public void onCallEnd(String callId) {
        java.util.concurrent.ScheduledFuture<?> sf = silenceTasks.remove(callId);
        if (sf != null) sf.cancel(false);
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
        wsClient.close(conversationId);
    }

    /** Scheduled tick — fires SILENCE_PROMPT to ai-conv when the caller has
     *  been quiet long enough and the bot isn't currently speaking. */
    private void tickSilence(String callId) {
        try {
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session == null) return;
            Map<String, Object> attrs = session.getProviderAttributes();
            if (Boolean.TRUE.equals(attrs.get("botSpeaking"))) return;

            long now = System.currentTimeMillis();
            long lastStt  = ((Number) attrs.getOrDefault("sttFinalAtMs", 0L)).longValue();
            long lastPrompt = ((Number) attrs.getOrDefault("lastSilencePromptAtMs", 0L)).longValue();
            long lastActivity = Math.max(lastStt, lastPrompt);

            if (now - lastActivity < SILENCE_THRESHOLD_MS) return;

            attrs.put("lastSilencePromptAtMs", now);
            log.info("[silence] callId={} conversationId={} silentMs={} — sending SILENCE_PROMPT",
                    callId, session.getConversationId(), now - lastActivity);
            wsClient.sendSilencePrompt(session.getConversationId());
        } catch (Exception e) {
            log.warn("Silence tick failed callId={}: {}", callId, e.getMessage());
        }
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
            if (session != null) {
                session.getTranscript().add(CallSession.TranscriptEntry.builder()
                        .speaker("assistant").text(text).timestamp(Instant.now()).build());
            }
            dispatch(l -> l.onAiReply(callId, text));
        }

        @Override
        public void onResponseDelta(String conversationId, String replyToMessageId, String deltaText) {
            if (deltaText == null || deltaText.isEmpty()) return;
            streamingAcc.append(deltaText);
            dispatch(l -> l.onAiReplyChunk(callId, deltaText));
        }

        @Override
        public void onResponseDone(String conversationId, String replyToMessageId, String finishReason) {
            String full = streamingAcc.toString();
            streamingAcc.setLength(0);
            if (!full.isBlank()) {
                CallSession session = callSessionRegistry.get(callId).orElse(null);
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
        public void onHangup(String conversationId, String replyToMessageId,
                             String spokenText, String reason) {
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session != null && spokenText != null && !spokenText.isBlank()) {
                session.getTranscript().add(CallSession.TranscriptEntry.builder()
                        .speaker("assistant").text(spokenText).timestamp(Instant.now()).build());
            }
            log.info("[hangup] callId={} reason={} spoken=\"{}\"", callId, reason, spokenText);
            // Single dispatch — the listener atomically synthesises the farewell
            // and then terminates the call so the TTS isn't cut off by a racing
            // executor task.
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