package com.aiassistant.callorchestration.transcription.elevenlabs;

import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.telephony.AudioCodec;
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

        URI uri = URI.create(endpoint + q);
        Listener listener = new Listener(callId, onTranscript, mapper);

        WebSocket ws;
        try {
            ws = httpClient.newWebSocketBuilder()
                    .header("xi-api-key", apiKey)
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, listener)
                    .join();
            log.info("[elevenlabs-stt] WS connected callId={}", callId);
        } catch (Exception ex) {
            log.error("[elevenlabs-stt] WS connect failed callId={}", callId, ex);
            throw new RuntimeException("ElevenLabs STT connect failed", ex);
        }

        return new ElevenLabsSttSession(callId, ws, mapper, sampleRateHz);
    }

    /** Per-session handle exposed to the caller. */
    private static class ElevenLabsSttSession implements SttSession {
        private static final Logger log = LoggerFactory.getLogger(ElevenLabsSttSession.class);
        private final String callId;
        private final WebSocket ws;
        private final ObjectMapper mapper;
        private final int sampleRateHz;
        // Serialise sends — java.net.http.WebSocket requires the previous send to complete first.
        private final AtomicReference<CompletableFuture<WebSocket>> sendChain =
                new AtomicReference<>(CompletableFuture.completedFuture(null));

        ElevenLabsSttSession(String callId, WebSocket ws, ObjectMapper mapper, int sampleRateHz) {
            this.callId = callId;
            this.ws = ws;
            this.mapper = mapper;
            this.sampleRateHz = sampleRateHz;
        }

        private long sentChunks = 0;

        @Override
        public void pushAudio(byte[] audio) {
            if (audio == null || audio.length == 0) return;
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
            // Chain sends so the previous one completes before we issue the next.
            sendChain.updateAndGet(prev -> prev.thenCompose(ignored -> ws.sendText(json, true))
                    .exceptionally(ex -> {
                        log.warn("[elevenlabs-stt] sendText failed callId={}: {}",
                                callId, ex.getMessage());
                        return ws;
                    }));
        }

        @Override
        public void close() {
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
        private final StringBuilder textBuffer = new StringBuilder();

        Listener(String callId, Consumer<TranscriptEvent> sink, ObjectMapper mapper) {
            this.callId = callId;
            this.sink = sink;
            this.mapper = mapper;
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
                            sink.accept(new TranscriptEvent(text, false, conf));
                        }
                    }
                    case "committed_transcript", "committed_transcript_with_timestamps" -> {
                        if (!text.isBlank()) {
                            sink.accept(new TranscriptEvent(text, true, conf));
                        }
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