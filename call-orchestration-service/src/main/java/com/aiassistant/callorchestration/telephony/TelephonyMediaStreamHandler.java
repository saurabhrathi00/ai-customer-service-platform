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
     * Raw text frame from the provider's WebSocket. The handler is responsible
     * for parsing its provider-specific JSON and dispatching to the
     * {@code onAudioFrame} / {@code onProviderEvent} methods below.
     */
    default void onInboundFrame(CallSession session, String payload) {
        // no-op default
    }

    void onAudioFrame(CallSession session, byte[] audioPayload, AudioCodec codec);

    void onProviderEvent(CallSession session, String eventType, Map<String, Object> payload);

    void onDisconnect(CallSession session, String reason);

    /** Encode an outbound audio chunk in this provider's expected format. */
    byte[] encodeOutboundAudio(CallSession session, byte[] pcm16k);
}
