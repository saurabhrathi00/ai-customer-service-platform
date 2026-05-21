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

    public interface CallEventListener {
        /** AI replied with a customer-facing text — push to TTS. */
        void onAiReply(String callId, String text);
        /** AI cannot answer — telephony should announce callback + hang up. */
        void onCallbackNeeded(String callId);
    }

    public void onCallStart(String callId, CallEventListener listener) {
        CallSession session = callSessionRegistry.get(callId)
                .orElseThrow(() -> new IllegalStateException("No CallSession for callId=" + callId));

        String knowledge = knowledgeServiceClient.fetchKnowledgeText(session.getBusinessId());
        session.setKnowledgeText(knowledge);

        listeners.put(callId, listener);
        wsClient.open(buildInit(session), new InboundDispatcher(callId));
        log.info("AI conversation started callId={} conversationId={}",
                callId, session.getConversationId());
    }

    public void onCustomerUtterance(String callId, String text) {
        if (text == null || text.isBlank()) return;
        String messageId = ULID_GEN.nextULID();
        CallSession session = callSessionRegistry.get(callId).orElse(null);
        if (session == null) {
            log.warn("No CallSession for utterance callId={}", callId);
            return;
        }
        session.getTranscript().add(CallSession.TranscriptEntry.builder()
                .speaker("customer").text(text).timestamp(Instant.now()).build());
        wsClient.sendUserMessage(session.getConversationId(), messageId, text);
    }

    public void onCallEnd(String callId) {
        CallSession session = callSessionRegistry.get(callId).orElse(null);
        String conversationId = session != null ? session.getConversationId() : callId;
        try {
            wsClient.close(conversationId);
        } finally {
            listeners.remove(callId);
        }
        log.info("AI conversation ended callId={} conversationId={}", callId, conversationId);
    }

    private AiConversationWsClient.InitPayload buildInit(CallSession session) {
        return new AiConversationWsClient.InitPayload(
                session.getConversationId(),
                session.getBusinessId(),
                session.getCallId(),
                session.getKnowledgeText(),
                session.getCustomerPhone(),
                session.getLanguage(),
                session.getProvider());
    }

    // ─── Inbound from ai-conversation-service ────────────────────────────

    private final class InboundDispatcher implements AiConversationCallbacks {

        private final String callId;

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
        public void onKnowledgeRequest(String conversationId) {
            log.info("ai-conv requested knowledge callId={}", callId);
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
        public void onCallbackNeeded(String conversationId, String replyToMessageId) {
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session != null) session.setCallbackRequested(true);
            log.info("Callback needed callId={}", callId);
            dispatch(l -> l.onCallbackNeeded(callId));
        }

        @Override
        public void onHistory(String conversationId, List<Map<String, String>> history) {
            CallSession session = callSessionRegistry.get(callId).orElse(null);
            if (session == null) {
                log.warn("HISTORY received but no CallSession callId={} conversationId={}",
                        callId, conversationId);
                return;
            }
            log.info("HISTORY received callId={} entries={}", callId,
                    history == null ? 0 : history.size());
            postCallOrchestrator.finalizeCall(session, history);
        }

        @Override
        public void onError(String conversationId, String code, String message) {
            log.warn("ai-conv error callId={} code={} msg={}", callId, code, message);
        }

        @Override
        public void onClosed(String conversationId, int closeCode, String reason) {
            log.info("ai-conv WS closed callId={} code={} reason={}", callId, closeCode, reason);
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