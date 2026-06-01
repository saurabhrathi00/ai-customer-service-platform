package com.aiassistant.callorchestration.telephony;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import de.huxhorn.sulky.ulid.ULID;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MediaStreamDispatcherHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MediaStreamDispatcherHandler.class);
    private static final Pattern WS_PATH = Pattern.compile(".*/ws/([^/]+)/call/([^/]+)");
    private static final ULID ULID_GEN = new ULID();

    private final TelephonyMediaStreamHandlerRegistry handlerRegistry;
    private final CallSessionRegistry callSessionRegistry;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession ws) throws Exception {
        PathParts parts = parsePath(ws);
        if (parts == null) {
            log.warn("WebSocket open with unparseable URI: {}", ws.getUri());
            ws.close(CloseStatus.BAD_DATA);
            return;
        }
        Optional<TelephonyMediaStreamHandler> handler = handlerRegistry.find(parts.provider);
        if (handler.isEmpty()) {
            log.warn("Unknown telephony provider on WS: {}", parts.provider);
            ws.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        CallSession session = CallSession.builder()
                .callId(parts.callId)
                .conversationId(ULID_GEN.nextULID())
                .provider(parts.provider)
                .language("en")
                .startedAt(Instant.now())
                .build();
        callSessionRegistry.put(session);
        ws.getAttributes().put("callId", parts.callId);
        ws.getAttributes().put("provider", parts.provider);
        session.getProviderAttributes().put("ws", ws);
        handler.get().onConnect(session, Map.of());
        log.info("WS connected provider={} callId={}", parts.provider, parts.callId);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession ws, @NonNull TextMessage message) {
        String callId = (String) ws.getAttributes().get("callId");
        String provider = (String) ws.getAttributes().get("provider");
        if (callId == null || provider == null) {
            return;
        }
        CallSession session = callSessionRegistry.get(callId).orElse(null);
        if (session == null) {
            log.warn("WS frame for unknown session callId={}", callId);
            return;
        }
        handlerRegistry.find(provider).ifPresent(h ->
                h.onInboundFrame(session, message.getPayload())
        );
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession ws, @NonNull CloseStatus status) {
        String callId = (String) ws.getAttributes().get("callId");
        String provider = (String) ws.getAttributes().get("provider");
        if (callId == null) return;
        callSessionRegistry.get(callId).ifPresent(session ->
                handlerRegistry.find(provider).ifPresent(h -> h.onDisconnect(session, status.getReason()))
        );
        callSessionRegistry.remove(callId);
        log.info("WS closed provider={} callId={} status={}", provider, callId, status);
    }

    private PathParts parsePath(WebSocketSession ws) {
        if (ws.getUri() == null) return null;
        Matcher m = WS_PATH.matcher(ws.getUri().getPath());
        if (!m.matches()) return null;
        return new PathParts(m.group(1), m.group(2));
    }

    private record PathParts(String provider, String callId) {}
}
