package com.aiassistant.callorchestration.telephony;

import org.springframework.http.server.ServerHttpRequest;

import java.util.Map;

public interface TelephonyMediaStreamHandler {

    String providerId();

    /**
     * Validate the HTTP upgrade request that opens this provider's WebSocket.
     * Deny-by-default — each provider must implement its own signature check.
     */
    default boolean validateHandshake(ServerHttpRequest request) {
        return false;
    }

    void onConnect(CallSession session, Map<String, String> connectParams);

    /**
     * Raw text frame from the provider's WebSocket. The handler parses
     * provider-specific JSON and routes audio frames internally.
     */
    default void onInboundFrame(CallSession session, String payload) {
        // no-op default
    }

    void onDisconnect(CallSession session, String reason);
}
