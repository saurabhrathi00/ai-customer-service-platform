package com.aiassistant.callorchestration.clients.ws;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.exceptions.DownstreamServiceException;
import com.aiassistant.callorchestration.security.ServiceTokenClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages one persistent WebSocket per active call to ai-conversation-service.
 *
 * <p>Outbound: {@code INIT}, {@code MESSAGE}, {@code END}.
 * Inbound: {@code RESPONSE}, {@code KNOWLEDGE_REQUEST}, {@code CALLBACK_NEEDED}, {@code ERROR}.
 *
 * <p>Per-conversation events are dispatched to an {@link AiConversationCallbacks}
 * supplied by the caller at {@link #open}.
 */
@Component
public class AiConversationWsClient {

    private static final Logger log = LoggerFactory.getLogger(AiConversationWsClient.class);
    private static final String AUDIENCE = "ai-conversation-service";
    private static final List<String> SCOPES = List.of("ai.internal.invoke");

    private final ServiceConfiguration serviceConfiguration;
    private final ServiceTokenClient serviceTokenClient;
    private final ObjectMapper mapper;
    private final WebSocketClient wsClient = new StandardWebSocketClient();

    private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();

    public AiConversationWsClient(ServiceConfiguration serviceConfiguration,
                                  ServiceTokenClient serviceTokenClient,
                                  ObjectMapper mapper) {
        this.serviceConfiguration = serviceConfiguration;
        this.serviceTokenClient = serviceTokenClient;
        this.mapper = mapper;
    }

    /**
     * Open a WS to ai-conversation-service for {@code conversationId} and
     * immediately send {@code INIT} with the rendered business knowledge.
     */
    public void open(InitPayload init, AiConversationCallbacks callbacks) {
        ServiceConfiguration.AiConversationService cfg = serviceConfiguration.getAiConversationService();
        if (cfg == null || cfg.getWsBaseUrl() == null || cfg.getWsBaseUrl().isBlank()) {
            throw new DownstreamServiceException("ai-conversation-service wsBaseUrl not configured");
        }
        URI uri = URI.create(cfg.getWsBaseUrl().replaceAll("/$", "") + "/" + init.conversationId());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenClient.getToken(AUDIENCE, SCOPES));

        Connection conn = new Connection(init.conversationId(), callbacks);
        try {
            WebSocketSession session = wsClient.execute(conn, headers, uri)
                    .get(cfg.getConnectTimeoutMs() > 0 ? cfg.getConnectTimeoutMs() : 5000,
                         TimeUnit.MILLISECONDS);
            conn.bind(session);
            connections.put(init.conversationId(), conn);
            sendInit(init);
            log.info("AI WS opened conversationId={} businessId={} callId={}",
                    init.conversationId(), init.businessId(), init.callId());
        } catch (Exception e) {
            connections.remove(init.conversationId());
            throw new DownstreamServiceException(
                    "Failed to open ai-conversation WS for " + init.conversationId(), e);
        }
    }

    /** INIT frame payload — caller assembles it once and reuses on KNOWLEDGE_REQUEST. */
    public record InitPayload(
            String conversationId,
            String businessId,
            String callId,
            String knowledge,
            String greeting,
            String customerPhone,
            String language,
            String provider
    ) {}

    /** Send the customer's utterance as a MESSAGE frame. */
    public void sendUserMessage(String conversationId, String messageId, String text) {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", WsMessageType.MESSAGE.name());
        body.put("conversationId", conversationId);
        if (messageId != null) body.put("messageId", messageId);
        body.put("text", text == null ? "" : text);
        sendJson(conversationId, body);
    }

    /** Signal ai-conv that the latest STT segment was below the confidence
     *  threshold. ai-conv responds with a canned "please repeat" reply
     *  (no LLM round-trip). */
    public void sendUnclearMessage(String conversationId, String messageId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", WsMessageType.UNCLEAR_MESSAGE.name());
        body.put("conversationId", conversationId);
        if (messageId != null) body.put("messageId", messageId);
        sendJson(conversationId, body);
    }

    /** Notify ai-conv that the caller has been silent for too long. ai-conv
     *  responds with a re-engagement prompt or, after a few repeats, HANGUP. */
    public void sendSilencePrompt(String conversationId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", WsMessageType.SILENCE_PROMPT.name());
        body.put("conversationId", conversationId);
        sendJson(conversationId, body);
    }

    /** Re-send INIT after a KNOWLEDGE_REQUEST (or as the first frame after open). */
    public void sendInit(InitPayload init) {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", WsMessageType.INIT.name());
        body.put("conversationId", init.conversationId());
        body.put("businessId", init.businessId());
        if (init.callId() != null) body.put("callId", init.callId());
        body.put("knowledge", init.knowledge() == null ? "" : init.knowledge());
        if (init.greeting() != null && !init.greeting().isBlank()) {
            body.put("greeting", init.greeting());
        }

        ObjectNode metadata = body.putObject("metadata");
        if (init.customerPhone() != null) metadata.put("customerPhone", init.customerPhone());
        if (init.language() != null) metadata.put("language", init.language());
        String provider = init.provider();
        if (provider == null || provider.isBlank()) {
            provider = serviceConfiguration.getAiConversationService().getProvider();
        }
        if (provider != null && !provider.isBlank()) metadata.put("provider", provider);

        sendJson(init.conversationId(), body);
    }

    /** Send END and close the WS for this conversation. */
    public void close(String conversationId) {
        Connection conn = connections.remove(conversationId);
        if (conn == null) return;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("type", WsMessageType.END.name());
            body.put("conversationId", conversationId);
            conn.sendText(mapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("Failed to send END conversationId={}: {}", conversationId, e.getMessage());
        }
        try {
            conn.close(CloseStatus.NORMAL);
        } catch (Exception e) {
            log.warn("Failed to close WS conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    public boolean isOpen(String conversationId) {
        Connection c = connections.get(conversationId);
        return c != null && c.isOpen();
    }

    @PreDestroy
    void shutdown() {
        connections.forEach((id, conn) -> {
            try { conn.close(CloseStatus.GOING_AWAY); } catch (Exception ignored) {}
        });
        connections.clear();
    }

    private void sendJson(String conversationId, Object payload) {
        Connection conn = connections.get(conversationId);
        if (conn == null || !conn.isOpen()) {
            throw new DownstreamServiceException(
                    "ai-conversation WS not open for conversationId=" + conversationId);
        }
        try {
            conn.sendText(mapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new DownstreamServiceException(
                    "Failed to send WS frame for conversationId=" + conversationId, e);
        }
    }

    // ─── Per-connection handler ──────────────────────────────────────────

    private final class Connection extends TextWebSocketHandler {

        private final String conversationId;
        private final AiConversationCallbacks callbacks;
        private volatile WebSocketSession session;

        Connection(String conversationId, AiConversationCallbacks callbacks) {
            this.conversationId = conversationId;
            this.callbacks = callbacks;
        }

        void bind(WebSocketSession s) { this.session = s; }
        boolean isOpen() { return session != null && session.isOpen(); }

        void sendText(String text) throws Exception {
            WebSocketSession s = session;
            if (s == null) throw new IllegalStateException("session not bound");
            synchronized (s) { s.sendMessage(new TextMessage(text)); }
        }

        void close(CloseStatus status) throws Exception {
            WebSocketSession s = session;
            if (s != null && s.isOpen()) s.close(status);
        }

        @Override
        protected void handleTextMessage(@NonNull WebSocketSession s, @NonNull TextMessage message) {
            try {
                JsonNode node = mapper.readTree(message.getPayload());
                String type = node.path("type").asText("");
                switch (WsMessageType.valueOf(type)) {
                    case RESPONSE -> callbacks.onResponse(
                            conversationId,
                            node.path("replyToMessageId").asText(null),
                            node.path("text").asText(""));
                    case RESPONSE_DELTA -> callbacks.onResponseDelta(
                            conversationId,
                            node.path("replyToMessageId").asText(null),
                            node.path("text").asText(""));
                    case RESPONSE_DONE -> callbacks.onResponseDone(
                            conversationId,
                            node.path("replyToMessageId").asText(null),
                            node.path("finishReason").asText(null));
                    case HANGUP -> {
                        JsonNode tNode = node.path("text");
                        String spoken = tNode.isMissingNode() || tNode.isNull()
                                ? null : tNode.asText(null);
                        callbacks.onHangup(
                                conversationId,
                                node.path("replyToMessageId").asText(null),
                                spoken,
                                node.path("reason").asText(null));
                    }
                    case KNOWLEDGE_REQUEST -> callbacks.onKnowledgeRequest(conversationId);
                    case CALLBACK_NEEDED -> {
                        JsonNode textNode = node.path("text");
                        String spoken = textNode.isMissingNode() || textNode.isNull()
                                ? null : textNode.asText(null);
                        callbacks.onCallbackNeeded(
                                conversationId,
                                node.path("replyToMessageId").asText(null),
                                spoken);
                    }
                    case HISTORY -> {
                        JsonNode hist = node.path("history");
                        List<Map<String, String>> parsed = hist.isMissingNode() || hist.isNull()
                                ? List.of()
                                : mapper.convertValue(hist, new TypeReference<List<Map<String, String>>>() {});
                        callbacks.onHistory(conversationId, parsed);
                    }
                    case ERROR -> callbacks.onError(
                            conversationId,
                            node.path("code").asText("ERROR"),
                            node.path("message").asText(""));
                    default -> log.warn("Unexpected inbound frame type={} conversationId={}",
                            type, conversationId);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Unknown frame type from ai-conversation conversationId={}: {}",
                        conversationId, message.getPayload());
            } catch (Exception e) {
                log.warn("Failed to handle frame conversationId={}: {}", conversationId, e.getMessage());
            }
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession s, @NonNull CloseStatus status) {
            connections.remove(conversationId, this);
            try {
                callbacks.onClosed(conversationId, status.getCode(), status.getReason());
            } catch (Exception e) {
                log.warn("onClosed handler failed conversationId={}: {}", conversationId, e.getMessage());
            }
        }
    }
}