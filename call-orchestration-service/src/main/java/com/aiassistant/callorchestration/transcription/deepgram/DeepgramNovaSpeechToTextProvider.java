package com.aiassistant.callorchestration.transcription.deepgram;

import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.telephony.AudioCodec;
import com.aiassistant.callorchestration.transcription.SpeechToTextProvider;
import com.aiassistant.callorchestration.transcription.SttSession;
import com.aiassistant.callorchestration.transcription.TranscriptEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Streams audio to Deepgram Nova-3 (the production "boring + correct" model)
 * via the {@code /v1/listen} streaming endpoint. Used when
 * {@code configs.stt.provider=deepgram-nova}.
 *
 * <p>Protocol differs from Flux:</p>
 * <ul>
 *   <li>Endpoint: {@code wss://api.deepgram.com/v1/listen} (NOT v2).</li>
 *   <li>Transcripts arrive as {@code type:"Results"} with
 *       {@code channel.alternatives[0].transcript} and an {@code is_final}
 *       boolean — interim partials and finals are both delivered.</li>
 *   <li>Server commits a final on a configurable {@code endpointing} silence
 *       window (default 10 ms VAD, we lift it to 400 ms to match Flux's
 *       behaviour).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "configs.stt.provider", havingValue = "deepgram-nova")
@RequiredArgsConstructor
public class DeepgramNovaSpeechToTextProvider implements SpeechToTextProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepgramNovaSpeechToTextProvider.class);

    private final SecretsConfiguration secrets;
    private final ServiceConfiguration configs;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String providerId() {
        return "deepgram-nova";
    }

    private static String encodingFor(AudioCodec codec) {
        return switch (codec) {
            case MULAW -> "mulaw";
            case PCM16 -> "linear16";
        };
    }

    @Override
    public SttSession openSession(String callId, AudioCodec codec, int sampleRateHz,
                                  Consumer<TranscriptEvent> onTranscript) {
        String apiKey = secrets.getDeepgram() == null ? null : secrets.getDeepgram().getKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Deepgram api key missing (secrets.deepgram.key)");
        }

        ServiceConfiguration.Deepgram dg = configs.getDeepgram();
        String model     = dg != null && dg.getNovaModelId() != null ? dg.getNovaModelId() : "nova-3-general";
        String language  = dg != null && dg.getNovaLanguage() != null ? dg.getNovaLanguage() : "multi";
        int endpointing  = dg != null && dg.getNovaEndpointingMs() != null ? dg.getNovaEndpointingMs() : 400;

        String q = "?model=" + model
                + "&encoding=" + encodingFor(codec)
                + "&sample_rate=" + sampleRateHz
                + "&language=" + language
                + "&channels=1"
                + "&interim_results=true"
                + "&punctuate=true"
                + "&smart_format=true"
                + "&endpointing=" + endpointing
                + "&vad_events=true";

        URI uri = URI.create("wss://api.deepgram.com/v1/listen" + q);
        Listener listener = new Listener(callId, onTranscript, mapper);

        WebSocket ws;
        try {
            ws = httpClient.newWebSocketBuilder()
                    .header("Authorization", "Token " + apiKey)
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, listener)
                    .join();
            log.info("[deepgram-nova-stt] WS connected callId={} model={} lang={} endpointing={}ms",
                    callId, model, language, endpointing);
        } catch (Exception ex) {
            log.error("[deepgram-nova-stt] WS connect failed callId={}", callId, ex);
            throw new RuntimeException("Deepgram Nova STT connect failed", ex);
        }
        return new NovaSttSession(callId, ws);
    }

    private static class NovaSttSession implements SttSession {
        private static final Logger log = LoggerFactory.getLogger(NovaSttSession.class);
        private final String callId;
        private final WebSocket ws;
        /** Chains binary sends per session — java.net.http.WebSocket disallows
         *  overlapping sends. CompletableFuture chaining keeps ordering without
         *  blocking the Tomcat NIO thread (previously waited up to 2s per
         *  failed frame, starving other calls). Transient failures are logged
         *  but DO NOT latch the session closed — every fresh frame is its own
         *  attempt so a one-off blip doesn't kill the rest of the call. */
        private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<WebSocket>> sendTail =
                new java.util.concurrent.atomic.AtomicReference<>(
                        java.util.concurrent.CompletableFuture.completedFuture(null));
        private final java.util.concurrent.atomic.AtomicLong failuresSinceLastLog =
                new java.util.concurrent.atomic.AtomicLong(0);

        NovaSttSession(String callId, WebSocket ws) {
            this.callId = callId;
            this.ws = ws;
        }

        @Override
        public void pushAudio(byte[] audio) {
            if (audio == null || audio.length == 0) return;
            ByteBuffer buf = ByteBuffer.wrap(audio);
            sendTail.updateAndGet(prev -> prev
                    .thenCompose(ignored -> ws.sendBinary(buf, true))
                    .exceptionally(ex -> {
                        // Rate-limited error logging: log every 50 consecutive
                        // failures so we surface the issue without flooding when
                        // Twilio replays the buffered tail of a torn-down call.
                        long n = failuresSinceLastLog.incrementAndGet();
                        if (n == 1 || n % 50 == 0) {
                            log.warn("[deepgram-nova-stt] sendBinary failed callId={} (count={}): {}",
                                    callId, n, ex.getMessage());
                        }
                        return ws;
                    }));
        }

        @Override
        public void close() {
            try {
                // Nova expects a JSON {type:"CloseStream"} terminator before WS close
                // — flushes any pending audio so the final transcript arrives.
                ws.sendText("{\"type\":\"CloseStream\"}", true)
                        .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> { log.debug("[deepgram-nova-stt] close-stream error: {}", ex.getMessage()); return ws; });
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "call ended")
                        .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> { log.debug("[deepgram-nova-stt] close error: {}", ex.getMessage()); return ws; });
            } catch (Exception ex) {
                log.debug("[deepgram-nova-stt] close threw callId={}: {}", callId, ex.getMessage());
            }
        }
    }

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
                try {
                    JsonNode root = mapper.readTree(full);
                    String type = root.path("type").asText("");
                    if ("Results".equals(type)) {
                        boolean isFinal = root.path("is_final").asBoolean(false);
                        JsonNode alt = root.path("channel").path("alternatives").path(0);
                        String text = alt.path("transcript").asText("");
                        Double conf = alt.has("confidence") ? alt.get("confidence").asDouble() : null;
                        if (!text.isBlank()) {
                            sink.accept(new TranscriptEvent(text, isFinal, conf));
                        }
                    } else if (!type.isEmpty()) {
                        log.debug("[deepgram-nova-stt] <- {} callId={}", type, callId);
                    }
                } catch (Exception ex) {
                    log.warn("[deepgram-nova-stt] parse error callId={} json={}", callId, full, ex);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("[deepgram-nova-stt] WS closed callId={} status={} reason={}", callId, statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("[deepgram-nova-stt] WS error callId={}", callId, error);
        }
    }
}
