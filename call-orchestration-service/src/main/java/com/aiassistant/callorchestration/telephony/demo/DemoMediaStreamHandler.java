package com.aiassistant.callorchestration.telephony.demo;

import com.aiassistant.callorchestration.clients.UserBusinessServiceClient;
import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.security.token.JwtTokenProvider;
import com.aiassistant.callorchestration.security.token.TokenPrincipal;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class DemoMediaStreamHandler implements TelephonyMediaStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(DemoMediaStreamHandler.class);
    private static final int MIN_FORWARD_CHARS = 2;
    private static final AudioCodec DEMO_CODEC = AudioCodec.PCM16;
    private static final int DEMO_SAMPLE_RATE = 16000;

    private final ServiceConfiguration serviceConfiguration;
    private final SpeechToTextProvider speechToTextProvider;
    private final TextToSpeechProvider textToSpeechProvider;
    private final ConversationCoordinator conversationCoordinator;
    private final UserBusinessServiceClient userBusinessServiceClient;
    private final JwtTokenProvider jwtTokenProvider;
    @Qualifier("ttsExecutor")
    private final Executor ttsExecutor;
    @Qualifier("silenceWatchdogScheduler")
    private final ScheduledExecutorService silenceWatchdogScheduler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String providerId() {
        return "demo";
    }

    @Override
    public boolean validateHandshake(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) return false;
        String token = extractParam(query, "token");
        if (token == null || token.isBlank()) {
            log.warn("[demo] handshake rejected — no token");
            return false;
        }
        if (!jwtTokenProvider.validate(token)) {
            log.warn("[demo] handshake rejected — invalid JWT");
            return false;
        }
        return true;
    }

    @Override
    public void onConnect(CallSession session, Map<String, String> connectParams) {
        log.info("[demo] onConnect callId={}", session.getCallId());

        WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
        if (ws == null) return;

        String query = (String) ws.getAttributes().get("queryString");
        if (query == null) {
            sendError(ws, "Missing query parameters");
            closeQuietly(ws);
            return;
        }

        String token = extractParam(query, "token");
        String businessId = extractParam(query, "businessId");

        if (token == null || businessId == null) {
            sendError(ws, "Missing token or businessId");
            closeQuietly(ws);
            return;
        }

        TokenPrincipal principal = jwtTokenProvider.parse(token);
        String tokenBusinessId = principal.getAttributes().get("businessId") != null
                ? principal.getAttributes().get("businessId").toString() : null;
        if (tokenBusinessId != null && !tokenBusinessId.equals(businessId)) {
            sendError(ws, "Token businessId mismatch");
            closeQuietly(ws);
            return;
        }

        UserBusinessServiceClient.DemoTimeResponse demoTime;
        try {
            demoTime = userBusinessServiceClient.getDemoTime(businessId);
        } catch (Exception ex) {
            log.error("[demo] failed to check demo time businessId={}: {}", businessId, ex.getMessage());
            sendError(ws, "Failed to verify demo eligibility");
            closeQuietly(ws);
            return;
        }

        if (demoTime == null || demoTime.secondsRemaining() <= 0) {
            sendEvent(ws, "demo_exhausted", Map.of("secondsRemaining", 0));
            closeQuietly(ws);
            return;
        }

        session.setBusinessId(businessId);
        session.getProviderAttributes().put("codec", DEMO_CODEC);
        session.getProviderAttributes().put("demoSecondsRemaining", demoTime.secondsRemaining());
        session.getProviderAttributes().put("demoStartMs", System.currentTimeMillis());

        sendEvent(ws, "started", Map.of("secondsRemaining", demoTime.secondsRemaining()));

        DemoCallEventListener listener = new DemoCallEventListener(session);
        session.getProviderAttributes().put("aiCallListener", listener);
        session.setLastCallerActivityMs(System.currentTimeMillis());

        startDemoTimer(session, listener, demoTime.secondsRemaining());
        startSilenceWatchdog(session, listener);

        try {
            conversationCoordinator.onCallStart(session.getCallId(), listener);
        } catch (Exception ex) {
            log.error("[demo] failed to open AI conversation callId={}", session.getCallId(), ex);
            sendError(ws, "Failed to start AI conversation");
            closeQuietly(ws);
            return;
        }

        try {
            SttSession stt = speechToTextProvider.openSession(
                    session.getCallId(), DEMO_CODEC, DEMO_SAMPLE_RATE,
                    sttEvent -> {
                        String text = sttEvent.text();
                        if (text == null || text.isBlank()) return;

                        if (!session.getGreetingDone().get()) return;

                        session.setLastCallerActivityMs(System.currentTimeMillis());
                        session.setSilenceNudgedAtMs(0L);

                        sendEvent(ws, "transcript",
                                Map.of("text", text, "isFinal", sttEvent.isFinal()));

                        if (!sttEvent.isFinal()) return;

                        String trimmed = text.trim();
                        if (trimmed.length() < MIN_FORWARD_CHARS) return;

                        conversationCoordinator.onCustomerUtterance(
                                session.getCallId(), text, true);
                    });
            session.getProviderAttributes().put("sttSession", stt);
        } catch (Exception ex) {
            log.error("[demo] failed to open STT session callId={}", session.getCallId(), ex);
        }
    }

    @Override
    public void onInboundFrame(CallSession session, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("unknown");
            switch (event) {
                case "media" -> {
                    String b64 = root.path("media").path("payload").asText(null);
                    if (b64 != null) {
                        byte[] audio = Base64.getDecoder().decode(b64);
                        Object stt = session.getProviderAttributes().get("sttSession");
                        if (stt instanceof SttSession sttSession) {
                            sttSession.pushAudio(audio);
                        }
                    }
                }
                case "stop" -> {
                    log.info("[demo] client sent stop callId={}", session.getCallId());
                    endDemo(session, "client_stop");
                }
                default -> log.debug("[demo] unknown event '{}' callId={}", event, session.getCallId());
            }
        } catch (Exception ex) {
            log.error("[demo] frame parse error callId={}", session.getCallId(), ex);
        }
    }

    @Override
    public void onDisconnect(CallSession session, String reason) {
        log.info("[demo] onDisconnect callId={} reason={}", session.getCallId(), reason);
        endDemo(session, reason != null ? reason : "disconnect");
    }

    private void endDemo(CallSession session, String reason) {
        cancelDemoTimer(session);
        cancelSilenceWatchdog(session);

        Object stt = session.getProviderAttributes().remove("sttSession");
        if (stt instanceof SttSession s) {
            try { s.close(); } catch (Exception ignored) {}
        }

        long startMs = session.getProviderAttributes().get("demoStartMs") instanceof Long l ? l : 0L;
        if (startMs > 0) {
            int elapsedSecs = (int) ((System.currentTimeMillis() - startMs) / 1000);
            if (elapsedSecs > 0) {
                userBusinessServiceClient.decrementDemoTime(session.getBusinessId(), elapsedSecs);
                log.info("[demo] decremented {}s of demo time businessId={}",
                        elapsedSecs, session.getBusinessId());
            }
        }

        try {
            conversationCoordinator.onCallEnd(session.getCallId());
        } catch (Exception ex) {
            log.warn("[demo] onCallEnd failed callId={}: {}", session.getCallId(), ex.getMessage());
        }
    }

    // ─── Demo timer ──────────────────────────────────────────────────

    private void startDemoTimer(CallSession session, DemoCallEventListener listener, int remainingSecs) {
        ScheduledFuture<?> timer = silenceWatchdogScheduler.schedule(() -> {
            log.info("[demo] TIME UP callId={}", session.getCallId());
            String farewell = "Your demo time has ended. Thank you for trying our service! "
                    + "Please subscribe to continue using our AI assistant.";
            listener.onHangup(session.getCallId(), farewell, "DEMO_TIME_UP");
        }, remainingSecs, TimeUnit.SECONDS);
        session.getProviderAttributes().put("demoTimer", timer);

        silenceWatchdogScheduler.scheduleAtFixedRate(() -> {
            long startMs = session.getProviderAttributes().get("demoStartMs") instanceof Long l ? l : 0L;
            int remaining = (int) session.getProviderAttributes()
                    .getOrDefault("demoSecondsRemaining", 0);
            int elapsed = (int) ((System.currentTimeMillis() - startMs) / 1000);
            int left = Math.max(0, remaining - elapsed);
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (ws != null && ws.isOpen()) {
                sendEvent(ws, "demo_time_update", Map.of("secondsRemaining", left));
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void cancelDemoTimer(CallSession session) {
        Object t = session.getProviderAttributes().remove("demoTimer");
        if (t instanceof ScheduledFuture<?> f) f.cancel(false);
    }

    // ─── Silence watchdog ────────────────────────────────────────────

    private void startSilenceWatchdog(CallSession session, DemoCallEventListener listener) {
        ServiceConfiguration.Silence cfg = serviceConfiguration.getSilence();
        if (cfg == null || !cfg.isEnabled()) return;
        long intervalMs = cfg.getCheckIntervalMs();
        ScheduledFuture<?> task = silenceWatchdogScheduler.scheduleWithFixedDelay(
                () -> tickSilenceWatchdog(session, listener, cfg),
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        session.getProviderAttributes().put("silenceWatchdog", task);
    }

    private void cancelSilenceWatchdog(CallSession session) {
        Object t = session.getProviderAttributes().remove("silenceWatchdog");
        if (t instanceof ScheduledFuture<?> f) f.cancel(false);
    }

    private void tickSilenceWatchdog(CallSession session, DemoCallEventListener listener,
                                     ServiceConfiguration.Silence cfg) {
        try {
            long now = System.currentTimeMillis();
            long anchor = Math.max(session.getLastTtsActivityMs(), session.getLastCallerActivityMs());
            long nudgedAt = session.getSilenceNudgedAtMs();
            if (nudgedAt > 0) {
                if (now - nudgedAt >= cfg.getHangupAfterNudgeMs()) {
                    cancelSilenceWatchdog(session);
                    String farewell = pickByLang(session, cfg.getFarewellTextHi(), cfg.getFarewellTextEn());
                    listener.onHangup(session.getCallId(), farewell, "SILENCE");
                }
                return;
            }
            if (now - anchor >= cfg.getNudgeAfterMs()) {
                String nudge = pickByLang(session, cfg.getNudgeTextHi(), cfg.getNudgeTextEn());
                session.setSilenceNudgedAtMs(now);
                listener.onAiReply(session.getCallId(), nudge);
            }
        } catch (Exception ex) {
            log.warn("[silence] demo watchdog tick failed callId={}: {}", session.getCallId(), ex.getMessage());
        }
    }

    private static String pickByLang(CallSession session, String hi, String en) {
        String lang = session.getLanguage();
        if (lang != null && lang.toLowerCase().startsWith("hi")) return hi;
        return en;
    }

    // ─── AI Call Event Listener ──────────────────────────────────────

    @RequiredArgsConstructor
    private final class DemoCallEventListener implements ConversationCoordinator.CallEventListener {

        private final CallSession session;
        private final AtomicReference<java.util.concurrent.CompletableFuture<Void>> ttsTail
                = new AtomicReference<>(java.util.concurrent.CompletableFuture.completedFuture(null));

        @Override
        public void onAiReply(String callId, String text) {
            session.recordBotUtterance(text);
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (ws != null && ws.isOpen()) {
                sendEvent(ws, "ai_reply", Map.of("text", text));
            }
            synthesize(callId, text);
        }

        @Override
        public void onAiReplyChunk(String callId, String deltaText) {
            if (deltaText == null || deltaText.isEmpty()) return;
            session.recordBotUtterance(deltaText);
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (ws != null && ws.isOpen()) {
                sendEvent(ws, "ai_reply_chunk", Map.of("text", deltaText));
            }
            synthesize(callId, deltaText);
        }

        @Override
        public void onCallbackNeeded(String callId) {
            log.info("[demo] callback needed callId={}", callId);
        }

        @Override
        public void onHangup(String callId, String spokenText, String reason) {
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            log.info("[demo] hangup callId={} reason={}", callId, reason);

            ttsTail.updateAndGet(prev -> prev.thenRunAsync(() -> {
                try {
                    if (spokenText != null && !spokenText.isBlank() && ws != null && ws.isOpen()) {
                        textToSpeechProvider.synthesizeStream(spokenText,
                                VoiceProfile.builder().language(session.getLanguage()).build(),
                                chunk -> sendAudioToClient(ws, chunk));
                        Thread.sleep(1500);
                    }
                } catch (Exception ex) {
                    log.warn("[demo] hangup TTS failed callId={}: {}", callId, ex.getMessage());
                }
                if (ws != null && ws.isOpen()) {
                    sendEvent(ws, "ended", Map.of("reason", reason));
                    closeQuietly(ws);
                }
            }, ttsExecutor).exceptionally(ex -> null));
        }

        private void synthesize(String callId, String text) {
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (ws == null || !ws.isOpen()) return;
            long myEpoch = session.getTtsEpoch().get();

            ttsTail.updateAndGet(prev -> prev.thenRunAsync(() -> {
                if (session.getTtsEpoch().get() != myEpoch) return;
                if (!ws.isOpen()) return;
                try {
                    textToSpeechProvider.synthesizeStream(text,
                            VoiceProfile.builder().language(session.getLanguage()).build(),
                            chunk -> {
                                if (session.getTtsEpoch().get() != myEpoch) {
                                    throw new RuntimeException("epoch stale");
                                }
                                sendAudioToClient(ws, chunk);
                            });
                } catch (Exception ex) {
                    if (!ex.getMessage().contains("epoch stale")) {
                        log.warn("[tts] FAIL callId={}: {}", callId, ex.getMessage());
                    }
                } finally {
                    session.setLastTtsActivityMs(System.currentTimeMillis());
                    if (session.getGreetingDone().compareAndSet(false, true)) {
                        log.info("[greeting] done — STT now active callId={}", callId);
                    }
                }
            }, ttsExecutor).exceptionally(ex -> null));
        }

        private void sendAudioToClient(WebSocketSession ws, byte[] mulawChunk) {
            if (!ws.isOpen()) return;
            try {
                byte[] pcm = mulawToPcm16(mulawChunk);
                ObjectNode frame = objectMapper.createObjectNode();
                frame.put("event", "media");
                frame.putObject("media")
                        .put("payload", Base64.getEncoder().encodeToString(pcm))
                        .put("sampleRate", 8000)
                        .put("encoding", "pcm16");
                synchronized (ws) {
                    ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    // ─── Utility ─────────────────────────────────────────────────────

    private void sendEvent(WebSocketSession ws, String event, Map<String, Object> data) {
        try {
            ObjectNode frame = objectMapper.createObjectNode();
            frame.put("event", event);
            data.forEach((k, v) -> {
                if (v instanceof Integer i) frame.put(k, i);
                else if (v instanceof Boolean b) frame.put(k, b);
                else frame.put(k, String.valueOf(v));
            });
            synchronized (ws) {
                ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            }
        } catch (Exception ex) {
            log.warn("[demo] sendEvent failed event={}: {}", event, ex.getMessage());
        }
    }

    private void sendError(WebSocketSession ws, String message) {
        sendEvent(ws, "error", Map.of("message", message));
    }

    private void closeQuietly(WebSocketSession ws) {
        try { ws.close(); } catch (Exception ignored) {}
    }

    private static String extractParam(String query, String key) {
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8);
                if (k.equals(key)) {
                    return java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private static final short[] MULAW_DECODE = new short[256];
    static {
        for (int i = 0; i < 256; i++) {
            int mu = ~i & 0xFF;
            int sign = (mu & 0x80) != 0 ? -1 : 1;
            int exponent = (mu >> 4) & 0x07;
            int mantissa = mu & 0x0F;
            int magnitude = ((mantissa << 1) + 33) << (exponent + 2);
            magnitude -= 0x84;
            MULAW_DECODE[i] = (short) (sign * magnitude);
        }
    }

    private static byte[] mulawToPcm16(byte[] mulaw) {
        byte[] pcm = new byte[mulaw.length * 2];
        for (int i = 0; i < mulaw.length; i++) {
            short sample = MULAW_DECODE[mulaw[i] & 0xFF];
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm;
    }
}
