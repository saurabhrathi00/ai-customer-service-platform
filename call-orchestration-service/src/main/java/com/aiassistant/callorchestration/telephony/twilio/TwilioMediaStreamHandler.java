package com.aiassistant.callorchestration.telephony.twilio;

import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.services.ConversationCoordinator;
import com.aiassistant.callorchestration.telephony.AudioCodec;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.TelephonyMediaStreamHandler;
import com.aiassistant.callorchestration.transcription.SpeechToTextProvider;
import com.aiassistant.callorchestration.transcription.SttSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.security.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TwilioMediaStreamHandler implements TelephonyMediaStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(TwilioMediaStreamHandler.class);
    private static final String SIGNATURE_HEADER = "X-Twilio-Signature";

    private final SecretsConfiguration secrets;
    private final SpeechToTextProvider speechToTextProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationCoordinator conversationCoordinator;

    @Override
    public String providerId() {
        return "twilio";
    }

    @Override
    public boolean validateHandshake(ServerHttpRequest request) {
        String authToken = secrets.getTwilio() == null ? null : secrets.getTwilio().getAuthToken();
        if (authToken == null || authToken.isBlank()) {
            log.error("[twilio] auth token missing in secrets — rejecting handshake");
            return false;
        }

        HttpHeaders headers = request.getHeaders();
        String signature = headers.getFirst(SIGNATURE_HEADER);
        if (signature == null || signature.isBlank()) {
            log.warn("[twilio] missing {} header — rejecting handshake", SIGNATURE_HEADER);
            return false;
        }

        String fullUrl = reconstructPublicUrl(request);
        try {
            boolean ok = new RequestValidator(authToken).validate(fullUrl, Collections.emptyMap(), signature);
            if (!ok) {
                log.warn("[twilio] signature mismatch url={} sig={}", fullUrl, signature);
            }
            return ok;
        } catch (RuntimeException ex) {
            log.error("[twilio] signature validation threw exception", ex);
            return false;
        }
    }

    /**
     * Twilio Media Streams signs the URL EXACTLY as it appears in the TwiML
     * {@code <Stream url="wss://..."/>} — so we must reconstruct with the
     * {@code wss://} scheme, not {@code https://}. Behind a reverse proxy
     * (Cloudflare tunnel, ngrok, etc.), use X-Forwarded-Host to recover the
     * public hostname since the request arrives on localhost:8086 internally.
     */
    private String reconstructPublicUrl(ServerHttpRequest request) {
        HttpHeaders h = request.getHeaders();
        String host = h.getFirst("X-Forwarded-Host");
        if (host == null) host = h.getFirst("Host");
        if (host == null) {
            // Last-resort fallback — swap to wss:// since Twilio signs that scheme for Streams
            String uri = request.getURI().toString();
            if (uri.startsWith("https://")) return "wss://" + uri.substring("https://".length());
            if (uri.startsWith("http://"))  return "wss://" + uri.substring("http://".length());
            return uri;
        }
        String path = request.getURI().getRawPath();
        String query = request.getURI().getRawQuery();
        return "wss://" + host + path + (query == null ? "" : "?" + query);
    }

    @Override
    public void onConnect(CallSession session, Map<String, String> connectParams) {
        log.debug("[twilio] onConnect callId={} params={}", session.getCallId(), connectParams);
    }

    @Override
    public void onInboundFrame(CallSession session, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("unknown");
            switch (event) {
                case "connected" -> log.debug("[twilio] <- CONNECTED callId={} protocol={} version={}",
                        session.getCallId(),
                        root.path("protocol").asText(),
                        root.path("version").asText());

                case "start" -> {
                    JsonNode start = root.path("start");
                    String streamSid = start.path("streamSid").asText();
                    String twilioCallSid = start.path("callSid").asText();
                    JsonNode fmt = start.path("mediaFormat");
                    Map<String, Object> info = new HashMap<>();
                    info.put("streamSid", streamSid);
                    info.put("twilioCallSid", twilioCallSid);
                    info.put("encoding", fmt.path("encoding").asText());
                    info.put("sampleRate", fmt.path("sampleRate").asInt());

                    // Stash streamSid on the session for outbound frames
                    session.getProviderAttributes().put("streamSid", streamSid);

                    Map<String, String> customParams = new HashMap<>();
                    JsonNode cp = start.path("customParameters");
                    Iterator<Map.Entry<String, JsonNode>> it = cp.fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> e = it.next();
                        customParams.put(e.getKey(), e.getValue().asText());
                    }
                    log.info("[twilio] call started callId={} format={}@{}Hz",
                            session.getCallId(),
                            fmt.path("encoding").asText(), fmt.path("sampleRate").asInt());

                    // Hydrate session with tenant context from TwiML <Parameter> tags
                    if (customParams.containsKey("businessId")) {
                        session.setBusinessId(customParams.get("businessId"));
                    }
                    if (customParams.containsKey("customerPhone")) {
                        session.setCustomerPhone(customParams.get("customerPhone"));
                    }


                    // Open the STT streaming session for this call
                    try {
                        int sampleRate = fmt.path("sampleRate").asInt(8000);
                        conversationCoordinator.onCallStart(
                                session.getCallId(),
                                new ConversationCoordinator.CallEventListener() {

                                    @Override
                                    public void onAiReply(String callId, String text) {

                                        log.info("[ai] reply callId={} text={}",
                                                callId, text);

                                        // TODO:
                                        // TTS generate karo
                                        // Twilio outbound media bhejo
                                    }

                                    @Override
                                    public void onCallbackNeeded(String callId) {

                                        log.info("[ai] callback needed callId={}",
                                                callId);

                                        // TODO:
                                        // callback flow
                                    }
                                }
                        );
                        SttSession stt = speechToTextProvider.openSession(
                                session.getCallId(),
                                AudioCodec.MULAW,
                                sampleRate,
                                event2 -> {
                                    if (event2.isFinal()) {
                                        long nowMs = System.currentTimeMillis();
                                        long firstFrameMs = ((Number) session.getProviderAttributes()
                                                .getOrDefault("firstFrameMs", 0L)).longValue();
                                        long sinceFirstFrame = firstFrameMs == 0 ? -1 : (nowMs - firstFrameMs);

                                        log.info("[stt] callId={} latencyFromFirstFrameMs={} text=\"{}\"",
                                                session.getCallId(), sinceFirstFrame, event2.text());
                                        session.getTranscript().add(CallSession.TranscriptEntry.builder()
                                                .speaker("CUSTOMER")
                                                .text(event2.text())
                                                .timestamp(java.time.Instant.now())
                                                .build());
                                        conversationCoordinator.onCustomerUtterance(
                                                session.getCallId(),
                                                event2.text()
                                        );
                                    }
                                }
                        );
                        session.getProviderAttributes().put("sttSession", stt);
                    } catch (Exception ex) {
                        log.error("[twilio] failed to open STT session callId={}", session.getCallId(), ex);
                    }

                    onProviderEvent(session, "start", info);
                }

                case "media" -> {
                    JsonNode media = root.path("media");
                    String b64 = media.path("payload").asText();
                    byte[] audio = Base64.getDecoder().decode(b64);

                    Map<String, Object> attrs = session.getProviderAttributes();
                    if (!attrs.containsKey("firstFrameMs")) {
                        attrs.put("firstFrameMs", System.currentTimeMillis());
                    }
                    onAudioFrame(session, audio, AudioCodec.MULAW);
                }

                case "mark" -> log.debug("[twilio] <- MARK callId={} name={}",
                        session.getCallId(), root.path("mark").path("name").asText());

                case "stop" -> {
                    log.info("[twilio] call stopped callId={}", session.getCallId());
                    onProviderEvent(session, "stop", Map.of());
                }

                case "dtmf" -> log.info("[twilio] DTMF callId={} digit={}",
                        session.getCallId(), root.path("dtmf").path("digit").asText());

                default -> log.warn("[twilio] <- UNKNOWN event '{}' callId={} payload={}",
                        event, session.getCallId(), payload);
            }
        } catch (Exception ex) {
            log.error("[twilio] frame parse error callId={} payload={}",
                    session.getCallId(), payload, ex);
        }
    }

    @Override
    public void onAudioFrame(CallSession session, byte[] audioPayload, AudioCodec codec) {
        Object stt = session.getProviderAttributes().get("sttSession");
        if (stt instanceof SttSession sttSession) {
            sttSession.pushAudio(audioPayload);
        }
    }

    @Override
    public void onProviderEvent(CallSession session, String eventType, Map<String, Object> payload) {
        log.debug("[twilio] onProviderEvent callId={} event={} payload={}",
                session.getCallId(), eventType, payload);
    }

    @Override
    public void onDisconnect(CallSession session, String reason) {
        log.info("[twilio] onDisconnect callId={} reason={}", session.getCallId(), reason);
    }

    @Override
    public byte[] encodeOutboundAudio(CallSession session, byte[] pcm16k) {
        return new byte[0];
    }
}