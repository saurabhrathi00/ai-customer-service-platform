package com.aiassistant.callorchestration.telephony.exotel;

import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.services.ConversationCoordinator;
import com.aiassistant.callorchestration.telephony.AudioCodec;
import com.aiassistant.callorchestration.telephony.BargeInHandler;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.TelephonyMediaStreamHandler;
import com.aiassistant.callorchestration.transcription.SpeechToTextProvider;
import com.aiassistant.callorchestration.transcription.SttSession;
import com.aiassistant.callorchestration.voice.FillerAudioCache;
import com.aiassistant.callorchestration.voice.TextToSpeechProvider;
import com.aiassistant.callorchestration.voice.VoiceProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Exotel Voicebot Applet media stream handler. Handles bidirectional
 * WebSocket audio streaming with Exotel using mu-law 8kHz codec
 * (same as Twilio — standard telephony G.711).
 *
 * <p>Exotel WebSocket frame format:
 * <ul>
 *   <li>{@code connected} — WebSocket established</li>
 *   <li>{@code start} — call metadata (streamSid, encoding, sampleRate)</li>
 *   <li>{@code media} — base64-encoded audio payload</li>
 *   <li>{@code stop} — call ended</li>
 *   <li>{@code dtmf} — keypress events</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "secrets.exotel", name = "apiKey")
public class ExotelMediaStreamHandler implements TelephonyMediaStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(ExotelMediaStreamHandler.class);

    private final SecretsConfiguration secrets;
    private final ServiceConfiguration serviceConfiguration;
    private final SpeechToTextProvider speechToTextProvider;
    private final TextToSpeechProvider textToSpeechProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationCoordinator conversationCoordinator;
    private final AiConversationWsClient aiConversationWsClient;
    private final FillerAudioCache fillerAudioCache;
    @Qualifier("ttsExecutor")
    private final Executor ttsExecutor;
    @Qualifier("silenceWatchdogScheduler")
    private final ScheduledExecutorService silenceWatchdogScheduler;

    private BargeInHandler bargeInHandler;

    @PostConstruct
    void initBargeIn() {
        this.bargeInHandler = new ExotelBargeInHandler(objectMapper);
    }

    private static final int    MIN_FORWARD_CHARS = 2;
    private static final Path   RECORDING_DIR = Path.of("/app/logs/recordings");

    @Override
    public String providerId() {
        return "exotel";
    }

    @Override
    public boolean validateHandshake(ServerHttpRequest request) {
        // Exotel opens this WebSocket after receiving the wss:// URL from our
        // webhook response. The URL itself is a shared secret (contains the
        // callId). Optionally validate a token query param if configured.
        // For now, accept all connections — the webhook already validated the
        // inbound call and the URL is unguessable (contains the call SID).
        log.info("[exotel] validating handshake uri={}", request.getURI());
        return true;
    }

    @Override
    public void onConnect(CallSession session, Map<String, String> connectParams) {
        log.info("[exotel] onConnect callId={}", session.getCallId());

        // Hydrate session from query params stored by handshake interceptor
        WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
        if (ws != null) {
            String query = (String) ws.getAttributes().get("queryString");
            log.info("[exotel] onConnect query from handshake attrs: {}", query);
            if (query != null) {
                Map<String, String> params = parseQueryParams(query);
                if (params.containsKey("businessId"))    session.setBusinessId(params.get("businessId"));
                if (params.containsKey("businessName"))  session.setBusinessName(params.get("businessName"));
                if (params.containsKey("customerPhone")) {
                    String phone = params.get("customerPhone");
                    if (phone != null && !phone.startsWith("+")) phone = "+" + phone;
                    session.setCustomerPhone(phone);
                }
                log.info("[exotel] session hydrated businessId={} businessName={} customerPhone={}",
                        session.getBusinessId(), session.getBusinessName(), session.getCustomerPhone());
            }
        }
    }

    @Override
    public void onInboundFrame(CallSession session, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("unknown");
            switch (event) {
                case "connected" -> log.info("[exotel] <- CONNECTED callId={} raw={}", session.getCallId(), payload);

                case "start" -> handleStart(session, root);

                case "media" -> {
                    String b64 = root.path("media").path("payload").asText(null);
                    if (b64 != null) {
                        byte[] audio = Base64.getDecoder().decode(b64);
                        onAudioFrame(session, audio);
                    }
                }

                case "stop" -> {
                    log.info("[exotel] call stopped callId={}", session.getCallId());
                    cancelSilenceWatchdog(session);
                    safeEndAiConversation(session.getCallId());
                }

                case "dtmf" -> log.info("[exotel] DTMF callId={} digit={}",
                        session.getCallId(), root.path("dtmf").path("digit").asText());

                default -> log.warn("[exotel] <- UNKNOWN event '{}' callId={}", event, session.getCallId());
            }
        } catch (Exception ex) {
            log.error("[exotel] frame parse error callId={} payload={}",
                    session.getCallId(), payload, ex);
        }
    }

    private void handleStart(CallSession session, JsonNode root) {
        log.info("[exotel] <- START raw callId={} payload={}", session.getCallId(), root.toString());
        JsonNode start = root.path("start");
        String streamSid = start.path("streamSid").asText(
                start.path("stream_sid").asText(session.getCallId()));
        String callSid = start.path("callSid").asText(
                start.path("call_sid").asText(session.getCallId()));
        JsonNode fmt = start.path("media_format");
        if (fmt.isMissingNode()) fmt = start.path("mediaFormat");
        String encoding = fmt.path("encoding").asText("audio/x-mulaw");
        int sampleRate = fmt.path("sample_rate").isMissingNode()
                ? fmt.path("sampleRate").asInt(8000)
                : fmt.path("sample_rate").asInt(8000);
        String bitRate = fmt.path("bit_rate").asText(fmt.path("bitRate").asText(""));

        session.getProviderAttributes().put("streamSid", streamSid);
        session.getProviderAttributes().put("exotelCallSid", callSid);

        // Detect codec: 128kbps@8kHz = 16 bits/sample = PCM16; 64kbps = mulaw
        AudioCodec codec;
        if (encoding.contains("l16") || encoding.contains("pcm")) {
            codec = AudioCodec.PCM16;
        } else if (bitRate.contains("128")) {
            codec = AudioCodec.PCM16;
        } else if (encoding.equals("base64")) {
            // Exotel sends "base64" as transport encoding; use bitrate to decide
            codec = AudioCodec.PCM16;
        } else {
            codec = AudioCodec.MULAW;
        }
        session.getProviderAttributes().put("codec", codec);

        log.info("[exotel] call started callId={} encoding={} sampleRate={} bitRate={} codec={}",
                session.getCallId(), encoding, sampleRate, bitRate, codec);

        session.getProviderAttributes().put("audioBuffer", new ByteArrayOutputStream());
        session.getProviderAttributes().put("audioSampleRate", sampleRate);

        // Hydrate from custom_parameters (Exotel uses snake_case)
        JsonNode cp = start.path("custom_parameters");
        if (cp.isMissingNode()) cp = start.path("customParameters");
        if (!cp.isMissingNode()) {
            if (cp.hasNonNull("businessId"))    session.setBusinessId(cp.path("businessId").asText());
            if (cp.hasNonNull("businessName"))  session.setBusinessName(cp.path("businessName").asText());
            if (cp.hasNonNull("customerPhone")) {
                String phone = cp.path("customerPhone").asText();
                if (!phone.startsWith("+")) phone = "+" + phone;
                session.setCustomerPhone(phone);
            }
            log.info("[exotel] session hydrated from start frame businessId={} customerPhone={}",
                    session.getBusinessId(), session.getCustomerPhone());
        }

        AiCallEventListener listener = new AiCallEventListener(session);
        session.getProviderAttributes().put("aiCallListener", listener);
        session.setLastCallerActivityMs(System.currentTimeMillis());
        startSilenceWatchdog(session, listener);

        try {
            conversationCoordinator.onCallStart(session.getCallId(), listener);
        } catch (Exception ex) {
            log.error("[exotel] failed to open AI conversation WS callId={}", session.getCallId(), ex);
            return;
        }

        try {
            SttSession stt = speechToTextProvider.openSession(
                    session.getCallId(), codec, sampleRate,
                    sttEvent -> {
                        String text = sttEvent.text();
                        if (text == null || text.isBlank()) return;

                        if (!session.getGreetingDone().get()) {
                            log.debug("[stt] dropped during greeting callId={} text=\"{}\"",
                                    session.getCallId(), text);
                            return;
                        }

                        session.setLastCallerActivityMs(System.currentTimeMillis());
                        session.setSilenceNudgedAtMs(0L);

                        if (!sttEvent.isFinal()) return;

                        String trimmed = text.trim();
                        if (trimmed.length() < MIN_FORWARD_CHARS) {
                            log.info("[stt] DROP-NOISE callId={} reason=too-short len={} text=\"{}\"",
                                    session.getCallId(), trimmed.length(), text);
                            return;
                        }

                        boolean barged = bargeInHandler.tryBargeIn(
                                session, trimmed, serviceConfiguration.getBargeIn());
                        if (barged) {
                            log.info("[exotel] barge-in accepted callId={} text=\"{}\"",
                                    session.getCallId(), trimmed);
                        }

                        if (!barged && fillerAudioCache.isEnabled()) {
                            AiCallEventListener l = (AiCallEventListener)
                                    session.getProviderAttributes().get("aiCallListener");
                            if (l != null) l.maybePlayFiller(text);
                        }
                        conversationCoordinator.onCustomerUtterance(
                                session.getCallId(), text, true);
                    }
            );
            session.getProviderAttributes().put("sttSession", stt);
        } catch (Exception ex) {
            log.error("[exotel] failed to open STT session callId={}", session.getCallId(), ex);
        }
    }

    private void onAudioFrame(CallSession session, byte[] audioPayload) {
        Object stt = session.getProviderAttributes().get("sttSession");
        if (stt instanceof SttSession sttSession) {
            sttSession.pushAudio(audioPayload);
        }
        Object buf = session.getProviderAttributes().get("audioBuffer");
        if (buf instanceof ByteArrayOutputStream baos) {
            synchronized (baos) { baos.write(audioPayload, 0, audioPayload.length); }
        }
        AudioCodec codec = (AudioCodec) session.getProviderAttributes()
                .getOrDefault("codec", AudioCodec.MULAW);
        if (session.getGreetingDone().get() && hasVoice(audioPayload, codec)) {
            session.setLastCallerActivityMs(System.currentTimeMillis());
            session.setSilenceNudgedAtMs(0L);
        }
    }

    private static final int VAD_MIN_VOICE_BYTES = 30;
    private static boolean hasVoice(byte[] frame, AudioCodec codec) {
        if (frame == null || frame.length == 0) return false;
        if (codec == AudioCodec.MULAW) {
            int nonSilence = 0;
            for (byte b : frame) {
                if (b != (byte) 0xFF && b != (byte) 0x7F) {
                    if (++nonSilence >= VAD_MIN_VOICE_BYTES) return true;
                }
            }
            return false;
        }
        // PCM16: silence is near-zero samples. Check 16-bit LE pairs.
        int threshold = 200;
        int voiced = 0;
        for (int i = 0; i + 1 < frame.length; i += 2) {
            short sample = (short) ((frame[i] & 0xFF) | (frame[i + 1] << 8));
            if (Math.abs(sample) > threshold) {
                if (++voiced >= VAD_MIN_VOICE_BYTES / 2) return true;
            }
        }
        return false;
    }

    @Override
    public void onDisconnect(CallSession session, String reason) {
        log.info("[exotel] onDisconnect callId={} reason={}", session.getCallId(), reason);
        dumpRecording(session);
        cancelSilenceWatchdog(session);
        safeEndAiConversation(session.getCallId());
    }

    private void dumpRecording(CallSession session) {
        Object buf = session.getProviderAttributes().remove("audioBuffer");
        if (!(buf instanceof ByteArrayOutputStream baos) || baos.size() == 0) return;
        try {
            AudioCodec codec = (AudioCodec) session.getProviderAttributes()
                    .getOrDefault("codec", AudioCodec.MULAW);
            int sampleRate = (int) session.getProviderAttributes()
                    .getOrDefault("audioSampleRate", 8000);

            byte[] raw = baos.toByteArray();
            byte[] pcm = (codec == AudioCodec.MULAW) ? mulawToPcm16(raw) : raw;

            Files.createDirectories(RECORDING_DIR);
            Path wavFile = RECORDING_DIR.resolve(session.getCallId() + ".wav");
            writeWav(wavFile, pcm, sampleRate);
            double durationSecs = (double) pcm.length / (sampleRate * 2);
            log.info("[recording] saved {} ({} bytes, {}s) callId={}",
                    wavFile, pcm.length, String.format("%.1f", durationSecs), session.getCallId());
        } catch (Exception ex) {
            log.error("[recording] failed to save callId={}: {}", session.getCallId(), ex.getMessage());
        }
    }

    private static void writeWav(Path path, byte[] pcmData, int sampleRate) throws IOException {
        int channels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            // RIFF header
            raf.writeBytes("RIFF");
            raf.write(intToLittleEndian(chunkSize));
            raf.writeBytes("WAVE");
            // fmt sub-chunk
            raf.writeBytes("fmt ");
            raf.write(intToLittleEndian(16));          // sub-chunk size
            raf.write(shortToLittleEndian((short) 1)); // PCM format
            raf.write(shortToLittleEndian((short) channels));
            raf.write(intToLittleEndian(sampleRate));
            raf.write(intToLittleEndian(byteRate));
            raf.write(shortToLittleEndian((short) blockAlign));
            raf.write(shortToLittleEndian((short) bitsPerSample));
            // data sub-chunk
            raf.writeBytes("data");
            raf.write(intToLittleEndian(dataSize));
            raf.write(pcmData);
        }
    }

    private static byte[] intToLittleEndian(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static byte[] shortToLittleEndian(short v) {
        return new byte[]{(byte) v, (byte) (v >> 8)};
    }

    // ─── Silence watchdog (mirrors Twilio handler) ─────────────────────

    private void startSilenceWatchdog(CallSession session, AiCallEventListener listener) {
        ServiceConfiguration.Silence cfg = serviceConfiguration.getSilence();
        if (cfg == null || !cfg.isEnabled()) return;
        long intervalMs = cfg.getCheckIntervalMs();
        ScheduledFuture<?> task = silenceWatchdogScheduler.scheduleWithFixedDelay(
                () -> tickSilenceWatchdog(session, listener, cfg),
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        session.getProviderAttributes().put("silenceWatchdog", task);
        log.info("[silence] watchdog started callId={} nudgeAfterMs={} hangupAfterNudgeMs={}",
                session.getCallId(), cfg.getNudgeAfterMs(), cfg.getHangupAfterNudgeMs());
    }

    private void cancelSilenceWatchdog(CallSession session) {
        Object t = session.getProviderAttributes().remove("silenceWatchdog");
        if (t instanceof ScheduledFuture<?> f) {
            f.cancel(false);
        }
    }

    private void tickSilenceWatchdog(CallSession session, AiCallEventListener listener,
                                     ServiceConfiguration.Silence cfg) {
        try {
            long now = System.currentTimeMillis();
            long anchor = Math.max(session.getLastTtsActivityMs(), session.getLastCallerActivityMs());

            long nudgedAt = session.getSilenceNudgedAtMs();
            if (nudgedAt > 0) {
                if (now - nudgedAt >= cfg.getHangupAfterNudgeMs()) {
                    log.info("[silence] HANGUP callId={}", session.getCallId());
                    cancelSilenceWatchdog(session);
                    String farewell = pickByLang(session, cfg.getFarewellTextHi(), cfg.getFarewellTextEn());
                    listener.onHangup(session.getCallId(), farewell, "SILENCE");
                }
                return;
            }

            if (now - anchor >= cfg.getNudgeAfterMs()) {
                String nudge = pickByLang(session, cfg.getNudgeTextHi(), cfg.getNudgeTextEn());
                log.info("[silence] NUDGE callId={}", session.getCallId());
                session.setSilenceNudgedAtMs(now);
                listener.onAiReply(session.getCallId(), nudge);
            }
        } catch (Exception ex) {
            log.warn("[silence] watchdog tick failed callId={}: {}", session.getCallId(), ex.getMessage());
        }
    }

    private static String pickByLang(CallSession session, String hi, String en) {
        String lang = session.getLanguage();
        if (lang != null && lang.toLowerCase().startsWith("hi")) return hi;
        return en;
    }

    private void safeEndAiConversation(String callId) {
        try {
            conversationCoordinator.onCallEnd(callId);
        } catch (Exception ex) {
            log.warn("[exotel] onCallEnd failed callId={}: {}", callId, ex.getMessage());
        }
    }

    // ─── Filler question detection (mirrors Twilio handler) ────────────

    private static final java.util.Set<String> QUESTION_WORDS = java.util.Set.of(
            "what", "where", "when", "why", "who", "how", "which", "whose",
            "can", "could", "do", "does", "did", "will", "would", "should",
            "is", "are", "am", "was", "were", "may", "might", "tell", "explain",
            "kya", "kaise", "kahan", "kab", "kyun", "kyon", "kaun", "kitna",
            "kitne", "kitni", "batao", "bataiye", "samjhao", "dikhao",
            "milega", "milegi", "hoga", "hogi");

    private static final int FILLER_MIN_CHARS = 14;

    private static boolean looksLikeFillerWorthy(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.length() < FILLER_MIN_CHARS) return false;
        if (trimmed.endsWith("?")) return true;
        String first = firstAlphaToken(trimmed);
        return first != null && QUESTION_WORDS.contains(first);
    }

    private static String firstAlphaToken(String text) {
        int n = text.length();
        int i = 0;
        while (i < n && !Character.isLetter(text.charAt(i))) i++;
        int start = i;
        while (i < n && (text.charAt(i) >= 'a' && text.charAt(i) <= 'z'
                       || text.charAt(i) >= 'A' && text.charAt(i) <= 'Z')) i++;
        return i == start ? null : text.substring(start, i).toLowerCase(java.util.Locale.ROOT);
    }

    // ─── Query param parser ────────────────────────────────────────────

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new java.util.HashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8);
                String val = java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                params.put(key, val);
            }
        }
        return params;
    }

    // ─── AI Call Event Listener ────────────────────────────────────────

    @RequiredArgsConstructor
    private final class AiCallEventListener implements ConversationCoordinator.CallEventListener {

        private final CallSession session;
        private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>> ttsTail
                = new java.util.concurrent.atomic.AtomicReference<>(java.util.concurrent.CompletableFuture.completedFuture(null));

        private volatile long lastFillerAtMs = 0L;
        private static final int FILLER_CHAIN_MAX = 3;
        private static final long FILLER_CHAIN_GAP_MS = 600L;
        private final java.util.concurrent.atomic.AtomicBoolean replyStartedForTurn =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        private final java.util.concurrent.atomic.AtomicInteger fillerChainCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        public void maybePlayFiller(String sttText) {
            if (!fillerAudioCache.isEnabled()) return;
            String trimmed = sttText == null ? "" : sttText.trim();
            long now = System.currentTimeMillis();
            if (now - lastFillerAtMs < fillerAudioCache.getMinGapMs()) return;
            boolean isQuestion = looksLikeFillerWorthy(trimmed);
            byte[] clip = isQuestion
                    ? fillerAudioCache.pickForText(sttText)
                    : fillerAudioCache.pickAckForText(sttText);
            if (clip == null || clip.length == 0) return;
            lastFillerAtMs = now;
            replyStartedForTurn.set(false);
            fillerChainCount.set(0);
            if (isQuestion) {
                queueChainedFiller(sttText, clip);
            } else {
                queueRawAudio(clip, "ack", fillerAudioCache.getStartDelayMs(), true);
            }
        }

        private void queueChainedFiller(String sttText, byte[] clip) {
            int idx = fillerChainCount.incrementAndGet();
            long delay = (idx == 1) ? fillerAudioCache.getStartDelayMs() : FILLER_CHAIN_GAP_MS;
            queueRawAudio(clip, "filler-" + idx, delay, true);
            long approxClipMs = clip.length / AudioCodec.MULAW.bytesPerMs();
            long checkAfterMs = delay + approxClipMs + 200L;
            silenceWatchdogScheduler.schedule(() -> chainTick(sttText), checkAfterMs, TimeUnit.MILLISECONDS);
        }

        private void chainTick(String sttText) {
            if (replyStartedForTurn.get()) return;
            if (session.getEndingCall().get()) return;
            if (fillerChainCount.get() >= FILLER_CHAIN_MAX) {
                playTroubleFallback();
                return;
            }
            byte[] next = fillerAudioCache.pickForText(sttText);
            if (next == null || next.length == 0) {
                playTroubleFallback();
                return;
            }
            queueChainedFiller(sttText, next);
        }

        private void playTroubleFallback() {
            if (!replyStartedForTurn.compareAndSet(false, true)) return;
            String msg = "Sorry, I'm having a little trouble right now. Could you please ask me again?";
            log.warn("[fallback] callId={} text=\"{}\"", session.getCallId(), msg);
            session.getTranscript().add(CallSession.TranscriptEntry.builder()
                    .speaker("assistant").text(msg).timestamp(java.time.Instant.now()).build());
            synthesize(session.getCallId(), msg);
        }

        private void paceForCarrierBuffer(long sendStartMs, long totalBytesSent) {
            ServiceConfiguration.BargeIn cfg = serviceConfiguration.getBargeIn();
            long maxBuf = cfg.getMaxBufferMs();
            if (maxBuf <= 0) return;
            long audioSentMs = totalBytesSent / AudioCodec.MULAW.bytesPerMs();
            long elapsedMs = System.currentTimeMillis() - sendStartMs;
            long bufferMs = audioSentMs - elapsedMs;
            if (bufferMs >= maxBuf) {
                try {
                    Thread.sleep(cfg.getDripIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void notePlayoutChunk(int bytesSent) {
            long chunkMs = bytesSent / AudioCodec.MULAW.bytesPerMs();
            long currentEnd = session.getEstimatedPlayoutEndMs();
            long now = System.currentTimeMillis();
            long newEnd = Math.max(currentEnd, now) + chunkMs;
            session.setEstimatedPlayoutEndMs(newEnd);
            if (session.getBotSpeakingStartMs() == 0) {
                session.setBotSpeakingStartMs(now);
            }
        }

        private void queueRawAudio(byte[] mulawBytes, String tag, long startDelayMs,
                                   boolean countAsBotSpeaking) {
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (streamSid == null || ws == null || !ws.isOpen()) return;
            long myEpoch = session.getTtsEpoch().get();
            ttsTail.updateAndGet(prev -> prev.thenRunAsync(() -> {
                if (session.getTtsEpoch().get() != myEpoch) return;
                if (!ws.isOpen()) return;
                if (startDelayMs > 0) {
                    try { Thread.sleep(startDelayMs); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    if (!ws.isOpen() || session.getTtsEpoch().get() != myEpoch) return;
                }
                try {
                    Base64.Encoder b64 = Base64.getEncoder();
                    int frame = 160;
                    int sent = 0;
                    long pacingStart = System.currentTimeMillis();
                    while (sent < mulawBytes.length) {
                        if (session.getTtsEpoch().get() != myEpoch) return;
                        int n = Math.min(frame, mulawBytes.length - sent);
                        byte[] piece = java.util.Arrays.copyOfRange(mulawBytes, sent, sent + n);
                        sent += n;
                        paceForCarrierBuffer(pacingStart, sent);
                        notePlayoutChunk(piece.length);
                        sendMediaChunk(ws, streamSid, b64, piece);
                    }
                } catch (Exception ex) {
                    log.warn("[exotel] {} send failed callId={}: {}", tag, session.getCallId(), ex.getMessage());
                } finally {
                    if (countAsBotSpeaking) {
                        session.setLastTtsActivityMs(System.currentTimeMillis());
                    }
                }
            }, ttsExecutor).exceptionally(ex -> null));
        }

        @Override
        public void onAiReply(String callId, String text) {
            replyStartedForTurn.set(true);
            session.recordBotUtterance(text);
            synthesize(callId, text);
        }

        @Override
        public void onAiReplyChunk(String callId, String deltaText) {
            if (deltaText == null || deltaText.isEmpty()) return;
            replyStartedForTurn.set(true);
            session.recordBotUtterance(deltaText);
            synthesize(callId, deltaText);
        }

        @Override
        public void onAiTransientFailure(String callId) {
            playTroubleFallback();
        }

        private void synthesize(String callId, String text) {
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (streamSid == null || ws == null || !ws.isOpen()) return;
            long myEpoch = session.getTtsEpoch().get();

            ttsTail.updateAndGet(prev -> prev.thenRunAsync(() -> {
                if (session.getTtsEpoch().get() != myEpoch) return;
                if (!ws.isOpen()) return;
                int[] totalBytes = {0};
                long[] pacingStart = {0};
                try {
                    Base64.Encoder b64 = Base64.getEncoder();
                    textToSpeechProvider.synthesizeStream(text,
                            VoiceProfile.builder().language(session.getLanguage()).build(),
                            chunk -> {
                                if (session.getTtsEpoch().get() != myEpoch) {
                                    throw new RuntimeException("barge-in: epoch stale");
                                }
                                if (totalBytes[0] == 0) {
                                    pacingStart[0] = System.currentTimeMillis();
                                    if (session.getTurnStartMs() > 0) {
                                        long sinceTurn = pacingStart[0] - session.getTurnStartMs();
                                        log.info("[latency] TTS-FIRST callId={} sttToFirstAudio={}ms", callId, sinceTurn);
                                    }
                                }
                                totalBytes[0] += chunk.length;
                                paceForCarrierBuffer(pacingStart[0], totalBytes[0]);
                                notePlayoutChunk(chunk.length);
                                sendMediaChunk(ws, streamSid, b64, chunk);
                            });
                } catch (Exception ex) {
                    if (!ex.getMessage().contains("barge-in")) {
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

        private void sendMediaChunk(WebSocketSession ws, String streamSid, Base64.Encoder b64, byte[] chunk) {
            if (!ws.isOpen()) return;
            try {
                // TTS outputs mulaw; Exotel expects PCM16 — convert on the fly
                AudioCodec codec = (AudioCodec) session.getProviderAttributes()
                        .getOrDefault("codec", AudioCodec.MULAW);
                byte[] outBytes = (codec == AudioCodec.PCM16) ? mulawToPcm16(chunk) : chunk;

                ObjectNode frame = objectMapper.createObjectNode();
                frame.put("event", "media");
                frame.put("streamSid", streamSid);
                frame.putObject("media").put("payload", b64.encodeToString(outBytes));
                synchronized (ws) {
                    ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void onCallbackNeeded(String callId) {
            log.info("[ai] callback needed callId={}", callId);
        }

        @Override
        public void onHangup(String callId, String spokenText, String reason) {
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            log.info("[ai] hangup callId={} reason={}", callId, reason);

            ttsTail.updateAndGet(prev -> prev.thenRunAsync(() -> {
                try {
                    if (spokenText != null && !spokenText.isBlank()
                            && streamSid != null && ws != null && ws.isOpen()) {
                        Base64.Encoder b64 = Base64.getEncoder();
                        long[] bytes = {0L};
                        textToSpeechProvider.synthesizeStream(spokenText,
                                VoiceProfile.builder().language(session.getLanguage()).build(),
                                chunk -> { bytes[0] += chunk.length; sendMediaChunk(ws, streamSid, b64, chunk); });
                        long playbackMs = bytes[0] / AudioCodec.MULAW.bytesPerMs() + 1200L;
                        Thread.sleep(playbackMs);
                    }
                } catch (Exception ex) {
                    log.warn("[exotel] hangup TTS failed callId={}: {}", callId, ex.getMessage());
                }
                closeExotelStream(callId, ws);
            }, ttsExecutor).exceptionally(ex -> { closeExotelStream(callId, (WebSocketSession) session.getProviderAttributes().get("ws")); return null; }));
        }

        private void closeExotelStream(String callId, WebSocketSession ws) {
            if (ws == null || !ws.isOpen()) return;
            try {
                // Send a stop event to signal Exotel we're done, then close
                ObjectNode frame = objectMapper.createObjectNode();
                frame.put("event", "stop");
                frame.put("streamSid", (String) session.getProviderAttributes().get("streamSid"));
                synchronized (ws) {
                    ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                }
                ws.close();
                log.info("[exotel] stream closed callId={}", callId);
            } catch (Exception ex) {
                log.warn("[exotel] stream close failed callId={}: {}", callId, ex.getMessage());
            }
        }
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
