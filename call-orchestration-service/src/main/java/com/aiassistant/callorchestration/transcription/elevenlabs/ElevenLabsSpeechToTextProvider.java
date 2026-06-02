package com.aiassistant.callorchestration.transcription.elevenlabs;

import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.telephony.AudioCodec;
import com.aiassistant.callorchestration.transcription.DevanagariTransliterator;
import com.aiassistant.callorchestration.transcription.SpeechToTextProvider;
import com.aiassistant.callorchestration.transcription.SttSession;
import com.aiassistant.callorchestration.transcription.TranscriptEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Streams audio to ElevenLabs realtime STT over WebSocket and surfaces
 * interim + final transcripts via callback. One session per call.
 *
 * Protocol (per ElevenLabs docs):
 *  - Outbound (client → EL): JSON text frames
 *      { "message_type":"input_audio_chunk", "audio_base_64":"...", "sample_rate":16000 }
 *  - Inbound (EL → client):  JSON text frames, message_type is one of
 *      "partial_transcript"   (interim — ignore for downstream AI)
 *      "committed_transcript" (final — push to AI)
 */
@Component
@ConditionalOnProperty(name = "configs.stt.provider", havingValue = "elevenlabs")
@RequiredArgsConstructor
public class ElevenLabsSpeechToTextProvider implements SpeechToTextProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsSpeechToTextProvider.class);

    private final SecretsConfiguration secrets;
    private final ServiceConfiguration configs;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String providerId() {
        return "elevenlabs";
    }

    /** Map (codec, sampleRate) to ElevenLabs' audio_format query param. */
    private static String toElevenLabsAudioFormat(AudioCodec codec, int sampleRateHz) {
        return switch (codec) {
            case MULAW -> "ulaw_8000";
            case PCM16 -> switch (sampleRateHz) {
                case 8000  -> "pcm_8000";
                case 16000 -> "pcm_16000";
                case 22050 -> "pcm_22050";
                case 24000 -> "pcm_24000";
                case 44100 -> "pcm_44100";
                case 48000 -> "pcm_48000";
                default -> throw new IllegalArgumentException(
                        "ElevenLabs STT does not support PCM sample rate " + sampleRateHz);
            };
        };
    }

    @Override
    public SttSession openSession(String callId, AudioCodec codec, int sampleRateHz,
                                  Consumer<TranscriptEvent> onTranscript) {
        String apiKey = secrets.getElevenlabs() == null ? null : secrets.getElevenlabs().getKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ElevenLabs api key missing (secrets.elevenlabs.key)");
        }

        ServiceConfiguration.ElevenLabs el = configs.getElevenlabs();
        if (el == null) {
            throw new IllegalStateException("ElevenLabs config missing (configs.elevenlabs.*)");
        }
        String endpoint = el.getSttWsUrl();
        String modelId = el.getSttModelId();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("ElevenLabs STT WS URL missing (configs.elevenlabs.sttWsUrl)");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException("ElevenLabs STT model id missing (configs.elevenlabs.sttModelId)");
        }

        String audioFormat = toElevenLabsAudioFormat(codec, sampleRateHz);

        StringBuilder q = new StringBuilder();
        q.append("?model_id=").append(modelId);
        q.append("&audio_format=").append(audioFormat);
        if (el.getSttLanguageCode() != null && !el.getSttLanguageCode().isBlank()) {
            q.append("&language_code=").append(el.getSttLanguageCode());
        }
        if (el.isSttIncludeLanguageDetection()) {
            q.append("&include_language_detection=true");
        }
        if (el.getSttCommitStrategy() != null && !el.getSttCommitStrategy().isBlank()) {
            q.append("&commit_strategy=").append(el.getSttCommitStrategy());
        }
        q.append("&no_verbatim=").append(el.isSttNoVerbatim());
        // VAD-mode params — only meaningful when commit_strategy=vad. Sending
        // them on a manual session is a no-op but the server still parses
        // them, so we tolerate either.
        if ("vad".equalsIgnoreCase(el.getSttCommitStrategy())) {
            if (el.getSttVadSilenceThresholdSecs() != null) {
                q.append("&vad_silence_threshold_secs=").append(el.getSttVadSilenceThresholdSecs());
            }
            if (el.getSttVadThreshold() != null) {
                q.append("&vad_threshold=").append(el.getSttVadThreshold());
            }
        }

        URI uri = URI.create(endpoint + q);

        long silenceMs = el.getSttManualCommitSilenceMs() != null
                ? el.getSttManualCommitSilenceMs() : 400L;
        boolean manual = "manual".equalsIgnoreCase(el.getSttCommitStrategy());
        ManualCommitState state = new ManualCommitState(silenceMs, manual);

        boolean transliterate = configs.getStt() != null && configs.getStt().isTransliterateDevanagari();
        Listener listener = new Listener(callId, onTranscript, mapper, state, transliterate);

        WebSocket ws;
        try {
            ws = httpClient.newWebSocketBuilder()
                    .header("xi-api-key", apiKey)
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, listener)
                    .join();
            log.info("[elevenlabs-stt] WS connected callId={} manualCommit={} silenceMs={}",
                    callId, manual, silenceMs);
        } catch (Exception ex) {
            log.error("[elevenlabs-stt] WS connect failed callId={}", callId, ex);
            throw new RuntimeException("ElevenLabs STT connect failed", ex);
        }

        return new ElevenLabsSttSession(callId, ws, mapper, sampleRateHz, state);
    }

    /** Shared state for manual-commit silence detection. */
    static class ManualCommitState {
        final long silenceMs;
        final boolean manual;
        /** Wall-clock of the last partial whose TEXT actually changed.
         *  ElevenLabs emits keepalive partials with unchanged text every
         *  ~1s while the caller is silent — those must NOT reset the
         *  silence clock or commit never fires. */
        volatile long lastPartialAtMs = 0;
        volatile String lastPartialText = "";
        final java.util.concurrent.atomic.AtomicBoolean hasUncommittedPartial =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        ManualCommitState(long silenceMs, boolean manual) {
            this.silenceMs = silenceMs;
            this.manual = manual;
        }
    }

    /** Per-session handle exposed to the caller. */
    private static class ElevenLabsSttSession implements SttSession {
        private static final Logger log = LoggerFactory.getLogger(ElevenLabsSttSession.class);
        private final String callId;
        private final WebSocket ws;
        private final ObjectMapper mapper;
        private final int sampleRateHz;
        private final ManualCommitState state;
        private final AtomicReference<CompletableFuture<WebSocket>> sendChain =
                new AtomicReference<>(CompletableFuture.completedFuture(null));
        private volatile boolean outputClosed = false;

        // Buffer small audio frames into ~200ms batches to avoid ElevenLabs queue_overflow.
        // At 8kHz mulaw, 200ms = 1600 bytes. We flush when the buffer reaches this
        // threshold OR when 200ms have elapsed since the first buffered byte.
        private static final int BUFFER_FLUSH_BYTES = 1600;
        private static final long BUFFER_FLUSH_MS = 200L;
        private final java.io.ByteArrayOutputStream audioBuffer = new java.io.ByteArrayOutputStream(BUFFER_FLUSH_BYTES * 2);
        private volatile long bufferFirstByteMs = 0;

        ElevenLabsSttSession(String callId, WebSocket ws, ObjectMapper mapper,
                             int sampleRateHz, ManualCommitState state) {
            this.callId = callId;
            this.ws = ws;
            this.mapper = mapper;
            this.sampleRateHz = sampleRateHz;
            this.state = state;
        }

        @Override
        public void pushAudio(byte[] audio) {
            if (audio == null || audio.length == 0) return;
            if (outputClosed) return;

            boolean shouldFlush;
            byte[] toSend = null;
            synchronized (audioBuffer) {
                if (audioBuffer.size() == 0) {
                    bufferFirstByteMs = System.currentTimeMillis();
                }
                audioBuffer.write(audio, 0, audio.length);
                shouldFlush = audioBuffer.size() >= BUFFER_FLUSH_BYTES
                        || (System.currentTimeMillis() - bufferFirstByteMs) >= BUFFER_FLUSH_MS;
                if (shouldFlush) {
                    toSend = audioBuffer.toByteArray();
                    audioBuffer.reset();
                }
            }

            if (shouldFlush && toSend != null) {
                sendAudioChunk(toSend);
            }

            if (state != null && state.manual && state.hasUncommittedPartial.get()) {
                long now = System.currentTimeMillis();
                if (now - state.lastPartialAtMs >= state.silenceMs
                        && state.hasUncommittedPartial.compareAndSet(true, false)) {
                    flushBuffer();
                    log.info("[elevenlabs-stt] callId={} sending manual commit after {} ms silence",
                            callId, now - state.lastPartialAtMs);
                    sendCommit();
                }
            }
        }

        private void flushBuffer() {
            byte[] toSend;
            synchronized (audioBuffer) {
                if (audioBuffer.size() == 0) return;
                toSend = audioBuffer.toByteArray();
                audioBuffer.reset();
            }
            sendAudioChunk(toSend);
        }

        private void sendAudioChunk(byte[] audio) {
            if (outputClosed) return;
            String json;
            try {
                ObjectNode msg = mapper.createObjectNode();
                msg.put("message_type", "input_audio_chunk");
                msg.put("audio_base_64", Base64.getEncoder().encodeToString(audio));
                msg.put("commit", false);
                msg.put("sample_rate", sampleRateHz);
                json = mapper.writeValueAsString(msg);
            } catch (Exception ex) {
                log.warn("[elevenlabs-stt] failed to serialise audio chunk callId={}: {}",
                        callId, ex.getMessage());
                return;
            }
            sendChain.updateAndGet(prev -> prev.thenCompose(ignored -> ws.sendText(json, true))
                    .exceptionally(ex -> {
                        if (!outputClosed) {
                            outputClosed = true;
                            log.warn("[elevenlabs-stt] sendText failed callId={} (suppressing further): {}",
                                    callId, ex.getMessage());
                        }
                        return ws;
                    }));
        }

        private void sendCommit() {
            if (outputClosed) return;
            String json;
            try {
                ObjectNode msg = mapper.createObjectNode();
                msg.put("message_type", "input_audio_chunk");
                msg.put("audio_base_64", "");
                msg.put("commit", true);
                msg.put("sample_rate", sampleRateHz);
                json = mapper.writeValueAsString(msg);
            } catch (Exception ex) {
                log.warn("[elevenlabs-stt] failed to serialise commit callId={}: {}",
                        callId, ex.getMessage());
                return;
            }
            sendChain.updateAndGet(prev -> prev.thenCompose(ignored -> ws.sendText(json, true))
                    .exceptionally(ex -> {
                        if (!outputClosed) {
                            outputClosed = true;
                            log.warn("[elevenlabs-stt] commit send failed callId={}: {}",
                                    callId, ex.getMessage());
                        }
                        return ws;
                    }));
        }

        @Override
        public void close() {
            outputClosed = true;
            flushBuffer();
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "call ended")
                        .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> { log.debug("[elevenlabs-stt] close error: {}", ex.getMessage()); return ws; });
                log.info("[elevenlabs-stt] WS close requested callId={}", callId);
            } catch (Exception ex) {
                log.debug("[elevenlabs-stt] close error callId={}: {}", callId, ex.getMessage());
            }
        }
    }

    /** Receives messages from ElevenLabs and emits transcript events. */
    private static class Listener implements WebSocket.Listener {
        private static final Logger log = LoggerFactory.getLogger(Listener.class);
        private final String callId;
        private final Consumer<TranscriptEvent> sink;
        private final ObjectMapper mapper;
        private final ManualCommitState state;
        private final StringBuilder textBuffer = new StringBuilder();
        /** Dedupe back-to-back duplicate finals — ElevenLabs Scribe realtime
         *  emits both {@code committed_transcript} and
         *  {@code committed_transcript_with_timestamps} for the same commit,
         *  which doubles every utterance and re-triggers barge-in on top of
         *  itself. Window is generous (1.5s) to also cover any retransmit. */
        private static final long DEDUPE_WINDOW_MS = 1500L;
        private volatile String lastFinalText = null;
        private volatile long lastFinalAtMs = 0L;
        private final boolean transliterate;

        Listener(String callId, Consumer<TranscriptEvent> sink, ObjectMapper mapper,
                 ManualCommitState state, boolean transliterate) {
            this.callId = callId;
            this.sink = sink;
            this.mapper = mapper;
            this.state = state;
            this.transliterate = transliterate;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String full = textBuffer.toString();
                textBuffer.setLength(0);
                handleMessage(full);
            }
            webSocket.request(1);
            return null;
        }

        private void handleMessage(String json) {
            try {
                JsonNode root = mapper.readTree(json);
                String messageType = root.path("message_type").asText("");
                String text = root.path("text").asText("");
                Double conf = root.has("confidence") ? root.get("confidence").asDouble() : null;

                switch (messageType) {
                    case "partial_transcript" -> {
                        if (!text.isBlank()) {
                            if (state != null && state.manual) {
                                // Only reset the silence clock when the
                                // partial text actually advances. Identical
                                // keepalive partials must NOT count as new
                                // speech, otherwise the silence threshold
                                // never elapses and commit never fires.
                                if (!text.equals(state.lastPartialText)) {
                                    state.lastPartialAtMs = System.currentTimeMillis();
                                    state.lastPartialText = text;
                                    state.hasUncommittedPartial.set(true);
                                    log.debug("[stt] PARTIAL  callId={} conf={} text=\"{}\"",
                                            callId, conf, text);
                                } else {
                                    log.debug("[stt] PARTIAL-KEEPALIVE callId={} text unchanged",
                                            callId);
                                }
                            } else {
                                log.debug("[stt] PARTIAL  callId={} conf={} text=\"{}\"",
                                        callId, conf, text);
                            }
                            sink.accept(new TranscriptEvent(
                                    transliterate ? DevanagariTransliterator.transliterate(text) : text, false, conf));
                        }
                    }
                    case "committed_transcript", "committed_transcript_with_timestamps" -> {
                        if (state != null && state.manual) {
                            state.hasUncommittedPartial.set(false);
                            // Reset so the next utterance's first partial is
                            // always treated as new text (it will likely be
                            // a different string anyway, but be defensive).
                            state.lastPartialText = "";
                        }
                        if (text.isBlank()) {
                            log.debug("[stt] FINAL-EMPTY callId={} type={}", callId, messageType);
                            break;
                        }
                        long now = System.currentTimeMillis();
                        if (text.equals(lastFinalText) && (now - lastFinalAtMs) < DEDUPE_WINDOW_MS) {
                            log.debug("[stt] FINAL-DEDUPE callId={} type={} sinceLast={}ms text=\"{}\"",
                                    callId, messageType, now - lastFinalAtMs, text);
                            break;
                        }
                        lastFinalText = text;
                        lastFinalAtMs = now;
                        String output = transliterate ? DevanagariTransliterator.transliterate(text) : text;
                        if (transliterate && !output.equals(text)) {
                            log.info("[stt] FINAL callId={} conf={} raw=\"{}\" transliterated=\"{}\"",
                                    callId, conf, text, output);
                        } else {
                            log.info("[stt] FINAL callId={} conf={} text=\"{}\"",
                                    callId, conf, output);
                        }
                        sink.accept(new TranscriptEvent(output, true, conf));
                    }
                    case "error", "auth_error", "quota_exceeded", "rate_limited",
                         "input_error", "chunk_size_exceeded", "transcriber_error" ->
                            log.error("[elevenlabs-stt] <- {} callId={} error={}",
                                    messageType, callId, root.path("error").asText(""));
                    default -> log.debug("[elevenlabs-stt] <- {} callId={} json={}",
                            messageType.isEmpty() ? "non-transcript" : messageType, callId, json);
                }
            } catch (Exception ex) {
                log.warn("[elevenlabs-stt] parse error callId={} json={}", callId, json, ex);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("[elevenlabs-stt] WS closed callId={} status={} reason={}", callId, statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("[elevenlabs-stt] WS error callId={}", callId, error);
        }
    }
}