package com.aiassistant.callorchestration.security;

import com.aiassistant.callorchestration.telephony.TelephonyMediaStreamHandler;
import com.aiassistant.callorchestration.telephony.TelephonyMediaStreamHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the HTTP upgrade request that opens a provider's WebSocket media
 * stream. Parses {provider} and {callId} from the URL, delegates signature
 * verification to the provider's {@link TelephonyMediaStreamHandler}, and
 * stashes the parsed values into the WebSocket session attributes so the
 * dispatcher does not need to re-parse them.
 */
@Component
@RequiredArgsConstructor
public class TelephonyHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TelephonyHandshakeInterceptor.class);
    private static final Pattern WS_PATH = Pattern.compile(".*/ws/([^/]+)/call/([^/]+)");

    public static final String ATTR_PROVIDER = "provider";
    public static final String ATTR_CALL_ID = "callId";

    private final TelephonyMediaStreamHandlerRegistry handlerRegistry;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        log.debug("WS handshake incoming: method={} uri={} headers={}",
                request.getMethod(), request.getURI(), request.getHeaders());

        String path = request.getURI().getPath();
        Matcher m = WS_PATH.matcher(path);
        if (!m.matches()) {
            log.warn("WS handshake REJECTED [400] — unparseable path {}", path);
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        String provider = m.group(1);
        String callId = m.group(2);

        Optional<TelephonyMediaStreamHandler> handler = handlerRegistry.find(provider);
        if (handler.isEmpty()) {
            log.warn("WS handshake REJECTED [404] — unknown provider {} (path={})", provider, path);
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        boolean valid;
        try {
            valid = handler.get().validateHandshake(request);
        } catch (RuntimeException ex) {
            log.error("WS handshake REJECTED [500] — validator threw exception", ex);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }

        if (!valid) {
            log.warn("WS handshake REJECTED [401] — signature invalid provider={} callId={}", provider, callId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(ATTR_PROVIDER, provider);
        attributes.put(ATTR_CALL_ID, callId);

        String query = request.getURI().getQuery();
        if (query != null && !query.isBlank()) {
            attributes.put("queryString", query);
        }

        log.info("WS handshake ACCEPTED provider={} callId={} query={}", provider, callId, query);
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.warn("WS handshake post-error: {}", exception.getMessage());
        }
    }
}