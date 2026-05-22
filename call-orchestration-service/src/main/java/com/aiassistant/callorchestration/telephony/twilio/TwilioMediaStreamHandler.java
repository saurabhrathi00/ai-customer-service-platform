package com.aiassistant.callorchestration.telephony.twilio;

import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.services.ConversationCoordinator;
import com.aiassistant.callorchestration.telephony.AudioCodec;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.TelephonyMediaStreamHandler;
import com.aiassistant.callorchestration.transcription.SpeechToTextProvider;
import com.aiassistant.callorchestration.transcription.SttSession;
import com.aiassistant.callorchestration.voice.TextToSpeechProvider;
import com.aiassistant.callorchestration.voice.VoiceProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.twilio.security.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.Executor;

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
    private final com.aiassistant.callorchestration.configuration.ServiceConfiguration serviceConfiguration;
    private final SpeechToTextProvider speechToTextProvider;
    private final TextToSpeechProvider textToSpeechProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationCoordinator conversationCoordinator;
    @Qualifier("ttsExecutor")
    private final Executor ttsExecutor;

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

                    // Stash streamSid + Twilio CallSid on the session — streamSid for
                    // outbound media frames, twilioCallSid for the REST hang-up call.
                    session.getProviderAttributes().put("streamSid", streamSid);
                    if (twilioCallSid != null && !twilioCallSid.isBlank()) {
                        session.getProviderAttributes().put("twilioCallSid", twilioCallSid);
                    }

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


                    // Open AI conversation WS — sends INIT with business knowledge,
                    // streams MESSAGE/RESPONSE for this call.
                    try {
                        conversationCoordinator.onCallStart(
                                session.getCallId(),
                                new AiCallEventListener(session));
                    } catch (Exception ex) {
                        log.error("[twilio] failed to open AI conversation WS callId={}",
                                session.getCallId(), ex);
                        // If AI WS fails to open, no point pumping audio into STT.
                        onProviderEvent(session, "start", info);
                        return;
                    }

                    // Open the STT streaming session — customer audio → text → coordinator.
                    try {
                        int sampleRate = fmt.path("sampleRate").asInt(8000);
                        SttSession stt = speechToTextProvider.openSession(
                                session.getCallId(),
                                AudioCodec.MULAW,
                                sampleRate,
                                event2 -> {
                                    if (event2.isFinal()) {
                                        // Confidence gate — sub-threshold transcripts get sent as
                                        // an UNCLEAR_MESSAGE frame; ai-conv short-circuits to a
                                        // canned "please repeat" reply (no LLM cost).
                                        var sttCfg = serviceConfiguration.getStt();
                                        Double threshold = sttCfg == null ? null : sttCfg.getConfidenceThreshold();
                                        boolean clear = !(threshold != null && event2.confidence() != null
                                                && event2.confidence() < threshold);
                                        if (!clear) {
                                            log.info("[stt] low-confidence callId={} conf={} text=\"{}\"",
                                                    session.getCallId(), event2.confidence(), event2.text());
                                        }
                                        long sttFinalAt = System.currentTimeMillis();
                                        session.getProviderAttributes().put("sttFinalAtMs", sttFinalAt);
                                        session.getProviderAttributes().remove("e2eLogged");
                                        session.getTranscript().add(CallSession.TranscriptEntry.builder()
                                                .speaker("CUSTOMER")
                                                .text(event2.text())
                                                .timestamp(java.time.Instant.now())
                                                .build());
                                        log.info("[latency] callId={} stage=stt-final",
                                                session.getCallId());
                                        conversationCoordinator.onCustomerUtterance(
                                                session.getCallId(),
                                                event2.text(),
                                                clear
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
                    safeEndAiConversation(session.getCallId());
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
        // Drop inbound audio while the bot is speaking. Without this the bot
        // hears its own TTS bleed back through the phone line and STT picks it
        // up as a "customer utterance" — derails the conversation. No barge-in
        // support today; this is the safe option.
        if (serviceConfiguration.getStt() != null
                && serviceConfiguration.getStt().isMuteWhileBotSpeaking()
                && Boolean.TRUE.equals(session.getProviderAttributes().get("botSpeaking"))) {
            return;
        }
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
        safeEndAiConversation(session.getCallId());
    }

    @Override
    public byte[] encodeOutboundAudio(CallSession session, byte[] pcm16k) {
        return new byte[0];
    }

    private void safeEndAiConversation(String callId) {
        try {
            conversationCoordinator.onCallEnd(callId);
        } catch (Exception ex) {
            log.warn("[twilio] onCallEnd failed callId={}: {}", callId, ex.getMessage());
        }
    }

    /**
     * Listener handed to the coordinator for a single call. Holds the
     * {@link CallSession} so future TTS / outbound-media work can read
     * provider attributes (streamSid, codec) without a registry lookup.
     */
    @RequiredArgsConstructor
    private final class AiCallEventListener implements ConversationCoordinator.CallEventListener {

        private final CallSession session;
        /** Per-call buffer for streaming text — flushed to TTS at sentence boundaries. */
        private final StringBuilder ttsBuffer = new StringBuilder();

        @Override
        public void onAiReply(String callId, String text) {
            synthesize(callId, text, true);
        }

        @Override
        public void onAiReplyChunk(String callId, String deltaText) {
            if (deltaText == null || deltaText.isEmpty()) return;
            synchronized (ttsBuffer) {
                ttsBuffer.append(deltaText);
                // Flush as soon as we have a sentence-boundary punctuation —
                // the earlier we hand text to TTS the lower the perceived
                // latency. Boundaries: . ! ? । (Devanagari danda) followed by
                // a space, or a newline.
                int flushAt = lastSentenceBoundary(ttsBuffer);
                if (flushAt > 0) {
                    String chunk = ttsBuffer.substring(0, flushAt).trim();
                    ttsBuffer.delete(0, flushAt);
                    if (!chunk.isEmpty()) {
                        synthesize(callId, chunk, false);
                    }
                }
            }
        }

        @Override
        public void onAiReplyDone(String callId) {
            String remaining;
            synchronized (ttsBuffer) {
                remaining = ttsBuffer.toString().trim();
                ttsBuffer.setLength(0);
            }
            if (!remaining.isEmpty()) {
                synthesize(callId, remaining, true);
            } else {
                String streamSid = (String) session.getProviderAttributes().get("streamSid");
                WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
                if (streamSid != null && ws != null && ws.isOpen()) {
                    ttsExecutor.execute(() -> {
                        try { sendMark(ws, streamSid, "ai-reply-end"); }
                        catch (Exception ex) {
                            log.warn("[twilio] mark send failed callId={}: {}", callId, ex.getMessage());
                        }
                    });
                }
            }
        }

        private void synthesize(String callId, String text, boolean isFinal) {
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (streamSid == null || ws == null || !ws.isOpen()) return;
            java.util.concurrent.atomic.AtomicInteger inflight =
                    (java.util.concurrent.atomic.AtomicInteger) session.getProviderAttributes()
                            .computeIfAbsent("ttsInflight", k -> new java.util.concurrent.atomic.AtomicInteger());
            inflight.incrementAndGet();
            session.getProviderAttributes().put("botSpeaking", Boolean.TRUE);
            ttsExecutor.execute(() -> {
                try {
                    Base64.Encoder b64 = Base64.getEncoder();
                    long ttsStart = System.currentTimeMillis();
                    long[] firstChunkAt = {-1};
                    textToSpeechProvider.synthesizeStream(text,
                            VoiceProfile.builder().language(session.getLanguage()).build(),
                            chunk -> {
                                if (firstChunkAt[0] < 0) {
                                    firstChunkAt[0] = System.currentTimeMillis();
                                    log.info("[latency] callId={} stage=tts-first-byte ms={}",
                                            callId, firstChunkAt[0] - ttsStart);
                                    // end2end logged ONCE per customer-turn — on the first
                                    // audio chunk of the first sentence after STT final.
                                    if (session.getProviderAttributes().putIfAbsent("e2eLogged", true) == null) {
                                        Object sttFinalAt = session.getProviderAttributes().get("sttFinalAtMs");
                                        if (sttFinalAt instanceof Long s && s > 0) {
                                            log.info("[latency] callId={} stage=end2end ms={}",
                                                    callId, firstChunkAt[0] - s);
                                        }
                                    }
                                }
                                sendMediaChunk(ws, streamSid, b64, chunk);
                            });
                    log.info("[latency] callId={} stage=tts-total ms={} chars={}",
                            callId, System.currentTimeMillis() - ttsStart, text.length());
                    if (isFinal) sendMark(ws, streamSid, "ai-reply-end");
                } catch (Exception ex) {
                    log.warn("[twilio] TTS/send failed callId={}: {}", callId, ex.getMessage());
                } finally {
                    if (inflight.decrementAndGet() <= 0) {
                        session.getProviderAttributes().put("botSpeaking", Boolean.FALSE);
                    }
                }
            });
        }

        /** Find the index just past the last sentence-ending punctuation in the buffer. */
        private int lastSentenceBoundary(CharSequence s) {
            int n = s.length();
            for (int i = n - 1; i >= 0; i--) {
                char c = s.charAt(i);
                if (c == '.' || c == '!' || c == '?' || c == '।' || c == '\n') {
                    // Require a following whitespace/newline (or end) to avoid splitting "Rs.500".
                    if (i == n - 1 || Character.isWhitespace(s.charAt(i + 1))) {
                        return i + 1;
                    }
                }
            }
            return -1;
        }

        private void sendMediaChunk(WebSocketSession ws, String streamSid, Base64.Encoder b64, byte[] chunk) {
            if (!ws.isOpen()) return;
            try {
                ObjectNode frame = objectMapper.createObjectNode();
                frame.put("event", "media");
                frame.put("streamSid", streamSid);
                frame.putObject("media").put("payload", b64.encodeToString(chunk));
                synchronized (ws) {
                    ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private void sendMark(WebSocketSession ws, String streamSid, String name) throws Exception {
            if (!ws.isOpen()) return;
            ObjectNode mark = objectMapper.createObjectNode();
            mark.put("event", "mark");
            mark.put("streamSid", streamSid);
            mark.putObject("mark").put("name", name);
            synchronized (ws) {
                ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(mark)));
            }
        }

        @Override
        public void onCallbackNeeded(String callId) {
            log.info("[ai] callback needed callId={}", callId);
            // TODO: announce callback via TTS, then hangup the Twilio stream
        }

        @Override
        public void onHangup(String callId, String spokenText, String reason) {
            String twilioCallSid = (String) session.getProviderAttributes().get("twilioCallSid");
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            log.info("[ai] hangup callId={} reason={} sid={}", callId, reason, twilioCallSid);

            // Run TTS + Twilio terminate inline on the same executor thread so
            // the farewell finishes playing before the call drops.
            ttsExecutor.execute(() -> {
                try {
                    if (spokenText != null && !spokenText.isBlank()
                            && streamSid != null && ws != null && ws.isOpen()) {
                        Base64.Encoder b64 = Base64.getEncoder();
                        textToSpeechProvider.synthesizeStream(spokenText,
                                VoiceProfile.builder().language(session.getLanguage()).build(),
                                chunk -> sendMediaChunk(ws, streamSid, b64, chunk));
                        sendMark(ws, streamSid, "ai-hangup-end");
                        // Small grace so the last audio frames buffered on Twilio's
                        // side actually reach the caller before we drop the call.
                        Thread.sleep(800);
                    }
                } catch (Exception ex) {
                    log.warn("[twilio] hangup TTS failed callId={}: {}", callId, ex.getMessage());
                }
                hangupTwilioCall(callId, twilioCallSid);
            });
        }

        private void hangupTwilioCall(String callId, String twilioCallSid) {
            if (twilioCallSid == null || twilioCallSid.isBlank()) {
                log.warn("[twilio] cannot hangup — no twilioCallSid callId={}", callId);
                return;
            }
            SecretsConfiguration.Twilio tw = secrets.getTwilio();
            if (tw == null || tw.getAccountSid() == null || tw.getAuthToken() == null) {
                log.warn("[twilio] cannot hangup — twilio credentials missing");
                return;
            }
            ensureTwilioInited(tw.getAccountSid(), tw.getAuthToken());
            try {
                com.twilio.rest.api.v2010.account.Call.updater(twilioCallSid)
                        .setStatus(com.twilio.rest.api.v2010.account.Call.UpdateStatus.COMPLETED)
                        .update();
                log.info("[twilio] call completed via REST callId={} sid={}", callId, twilioCallSid);
            } catch (Exception ex) {
                log.warn("[twilio] hangup REST failed callId={}: {}", callId, ex.getMessage());
            }
        }
    }

    private static volatile boolean twilioInited = false;
    private static void ensureTwilioInited(String accountSid, String authToken) {
        if (twilioInited) return;
        synchronized (TwilioMediaStreamHandler.class) {
            if (!twilioInited) {
                com.twilio.Twilio.init(accountSid, authToken);
                twilioInited = true;
            }
        }
    }
}