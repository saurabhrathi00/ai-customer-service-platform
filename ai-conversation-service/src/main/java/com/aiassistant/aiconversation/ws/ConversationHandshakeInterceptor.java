package com.aiassistant.aiconversation.ws;

import com.aiassistant.aiconversation.security.token.TokenPrincipal;
import com.aiassistant.aiconversation.security.token.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ConversationHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ConversationHandshakeInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REQUIRED_SCOPE = "ai.internal.invoke";

    public static final String ATTR_SUBJECT = "tokenSubject";
    public static final String ATTR_SCOPES = "tokenScopes";

    private final TokenProvider tokenProvider;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        String header = firstHeader(request, HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || !tokenProvider.validate(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            TokenPrincipal parsed = tokenProvider.parse(token);
            List<String> scopes = asStringList(parsed.getAttributes().get("scopes"));
            if (scopes.isEmpty()) scopes = asStringList(parsed.getAttributes().get("scope"));
            if (!scopes.contains(REQUIRED_SCOPE)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(ATTR_SUBJECT, parsed.getSubject());
            attributes.put(ATTR_SCOPES, scopes);

            if (request instanceof ServletServerHttpRequest sreq) {
                String conversationId = extractConversationId(sreq.getServletRequest().getRequestURI());
                if (conversationId != null) {
                    attributes.put(ConversationWebSocketHandler.ATTR_PATH_CONVERSATION_ID, conversationId);
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("WS handshake auth failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private static String firstHeader(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String extractConversationId(String uri) {
        if (uri == null) return null;
        int idx = uri.indexOf("/ws/conversation/");
        if (idx < 0) return null;
        String tail = uri.substring(idx + "/ws/conversation/".length());
        int slash = tail.indexOf('/');
        return slash < 0 ? tail : tail.substring(0, slash);
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof Collection<?> c) {
            return c.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (v instanceof String s) return List.of(s);
        return List.of();
    }
}
