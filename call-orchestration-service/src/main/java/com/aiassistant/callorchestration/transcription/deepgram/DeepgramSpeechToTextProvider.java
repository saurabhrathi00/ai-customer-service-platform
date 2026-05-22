package com.aiassistant.callorchestration.transcription.deepgram;

import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
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
import java.util.function.Consumer;

/**
 * Streams audio to Deepgram Flux realtime STT and surfaces transcripts via
 * callback. One session per call.
 *
 * Flux protocol (per https://developers.deepgram.com/docs/flux/quickstart):
 *  - Connect: wss://api.deepgram.com/v2/listen?model=flux-general-multi&encoding=mulaw&sample_rate=8000&language_hint=hi&language_hint=en
 *    with header  Authorization: Token <api-key>
 *  - Outbound (client → DG): BINARY frames carrying raw audio bytes.
 *  - Inbound  (DG → client):  JSON text frames. The transcript-bearing messages
 *    are { "type":"TurnInfo", "transcript":"...", "words":[ {word, confidence}, ... ] }.
 *    Flux has no explicit interim/final flag — a non-empty transcript = finalised turn.
 *
 * Note: Flux requires /v2/listen. /v1/listen will not work with these models.
 */
@Component
@ConditionalOnProperty(name = "configs.stt.provider", havingValue = "deepgram")
@RequiredArgsConstructor
public class DeepgramSpeechToTextProvider implements SpeechToTextProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepgramSpeechToTextProvider.class);

    private final SecretsConfiguration secrets;
    private final com.aiassistant.callorchestration.configuration.ServiceConfiguration configs;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String providerId() {
        return "deepgram";
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

        // Model + eot_timeout come from configs.deepgram.* — see
        // ServiceConfiguration.Deepgram for defaults. Single-language models
        // (e.g. flux-general-en) are faster and more accurate when the language
        // is known up front; eot_timeout caps how long the server waits in
        // silence before forcing end-of-turn.
        var dg = configs.getDeepgram();
        String modelId = (dg != null && dg.getSttModelId() != null) ? dg.getSttModelId() : "flux-general-en";
        int eotMs = (dg != null && dg.getSttEotTimeoutMs() != null) ? dg.getSttEotTimeoutMs() : 400;
        String q = "?model=" + modelId
                + "&encoding=" + encodingFor(codec)
                + "&sample_rate=" + sampleRateHz
                + "&eot_timeout_ms=" + eotMs;

        URI uri = URI.create("wss://api.deepgram.com/v2/listen" + q);
        Listener listener = new Listener(callId, onTranscript, mapper);

        WebSocket ws;
        try {
            ws = httpClient.newWebSocketBuilder()
                    .header("Authorization", "Token " + apiKey)
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, listener)
                    .join();
            log.info("[deepgram-stt] WS connected callId={} url={}", callId, uri);
        } catch (Exception ex) {
            log.error("[deepgram-stt] WS connect failed callId={}", callId, ex);
            throw new RuntimeException("Deepgram STT connect failed", ex);
        }
        return new DeepgramSttSession(callId, ws);
    }

    private static class DeepgramSttSession implements SttSession {
        private static final Logger log = LoggerFactory.getLogger(DeepgramSttSession.class);
        private final String callId;
        private final WebSocket ws;
        private final Object sendLock = new Object();
        private long sentFrames = 0;

        DeepgramSttSession(String callId, WebSocket ws) {
            this.callId = callId;
            this.ws = ws;
        }

        @Override
        public void pushAudio(byte[] audio) {
            if (audio == null || audio.length == 0) return;
            // Java's HttpClient WebSocket disallows overlapping sends. Serialise
            // with a lock and block on each send so we can be sure bytes actually
            // hit the wire.
            synchronized (sendLock) {
                try {
                    ws.sendBinary(ByteBuffer.wrap(audio), true)
                            .get(2, java.util.concurrent.TimeUnit.SECONDS);
                    sentFrames++;
                    if (sentFrames == 1) {
                        log.debug("[deepgram-stt] first frame sent callId={}", callId);
                    }
                } catch (Exception ex) {
                    log.warn("[deepgram-stt] sendBinary failed callId={} after {} frames: {}",
                            callId, sentFrames, ex.getMessage());
                }
            }
        }

        @Override
        public void close() {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "call ended")
                        .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> { log.debug("[deepgram-stt] close error: {}", ex.getMessage()); return ws; });
            } catch (Exception ex) {
                log.debug("[deepgram-stt] close threw callId={}: {}", callId, ex.getMessage());
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
                    // Flux TurnInfo messages have an `event` field. We only treat
                    // EndOfTurn as a final transcript that goes to the LLM; Update
                    // and EagerEndOfTurn are interim and only used to keep the
                    // silence-detector activity timestamp fresh.
                    if ("TurnInfo".equals(type)) {
                        String event = root.path("event").asText("");
                        String text = root.path("transcript").asText("");
                        Double conf = null;
                        JsonNode words = root.path("words");
                        if (words.isArray() && !words.isEmpty()) {
                            double min = 1.0;
                            for (JsonNode w : words) {
                                if (w.has("confidence")) {
                                    min = Math.min(min, w.get("confidence").asDouble());
                                }
                            }
                            conf = min;
                        }
                        boolean isFinal = "EndOfTurn".equals(event);
                        if (!text.isBlank()) {
                            try {
                                sink.accept(new TranscriptEvent(text, isFinal, conf));
                            } catch (Exception ex) {
                                log.warn("[deepgram-stt] sink error callId={}: {}",
                                        callId, ex.getMessage());
                            }
                        }
                    } else if (!type.isEmpty()) {
                        log.debug("[deepgram-stt] <- {} callId={}", type, callId);
                    }
                } catch (Exception ex) {
                    log.warn("[deepgram-stt] parse error callId={} json={}", callId, full, ex);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("[deepgram-stt] WS closed callId={} status={} reason={}", callId, statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("[deepgram-stt] WS error callId={}", callId, error);
        }
    }
}