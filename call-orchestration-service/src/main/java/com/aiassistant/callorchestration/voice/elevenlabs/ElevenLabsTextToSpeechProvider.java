package com.aiassistant.callorchestration.voice.elevenlabs;

import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.exceptions.DownstreamServiceException;
import com.aiassistant.callorchestration.voice.TextToSpeechProvider;
import com.aiassistant.callorchestration.voice.VoiceProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "configs.tts.provider", havingValue = "elevenlabs", matchIfMissing = true)
public class ElevenLabsTextToSpeechProvider implements TextToSpeechProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsTextToSpeechProvider.class);

    /** Twilio expects 20 ms mu-law frames @ 8 kHz = 160 bytes. */
    private static final int TWILIO_FRAME_BYTES = 160;

    private final ServiceConfiguration serviceConfiguration;
    private final SecretsConfiguration secretsConfiguration;
    private final RestClient restClient;

    public ElevenLabsTextToSpeechProvider(ServiceConfiguration serviceConfiguration,
                                          SecretsConfiguration secretsConfiguration) {
        this.serviceConfiguration = serviceConfiguration;
        this.secretsConfiguration = secretsConfiguration;
        ServiceConfiguration.ElevenLabs cfg = serviceConfiguration.getElevenlabs();
        String baseUrl = cfg != null && cfg.getTtsBaseUrl() != null
                ? cfg.getTtsBaseUrl() : "https://api.elevenlabs.io";
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String providerId() {
        return "elevenlabs";
    }

    /**
     * Cold TLS + DNS + HTTP/2 handshake to ElevenLabs can add 3-4s to the
     * first real synthesis. Fire a tiny throwaway request at startup so the
     * HTTP connection pool is warm before the first call arrives.
     */
    @PostConstruct
    void warmUp() {
        try {
            synthesizeStream(".", null, chunk -> { /* discard */ });
            log.info("[elevenlabs] tts warm-up complete");
        } catch (Exception ex) {
            log.warn("[elevenlabs] tts warm-up failed (non-fatal): {}", ex.getMessage());
        }
    }

    @Override
    public byte[] synthesize(String text, VoiceProfile voice) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        synthesizeStream(text, voice, chunk -> out.write(chunk, 0, chunk.length));
        return out.toByteArray();
    }

    @Override
    public void synthesizeStream(String text, VoiceProfile voice, Consumer<byte[]> onChunk) {
        if (text == null || text.isBlank()) return;

        ServiceConfiguration.ElevenLabs cfg = serviceConfiguration.getElevenlabs();
        String apiKey = secretsConfiguration.getElevenlabs() == null
                ? null : secretsConfiguration.getElevenlabs().getKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new DownstreamServiceException("ElevenLabs api key not configured");
        }

        String voiceId = voice != null && voice.getVoiceId() != null && !voice.getVoiceId().isBlank()
                ? voice.getVoiceId() : cfg.getTtsVoiceId();
        String modelId = cfg.getTtsModelId();
        String outputFormat = cfg.getTtsOutputFormat() == null ? "ulaw_8000" : cfg.getTtsOutputFormat();

        long start = System.currentTimeMillis();
        long[] firstChunkAt = {-1};
        int[] totalBytes = {0};

        try {
            restClient.post()
                    .uri(uri -> uri.path("/v1/text-to-speech/{voiceId}/stream")
                            .queryParam("output_format", outputFormat)
                            .build(voiceId))
                    .header("xi-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(text, modelId, cfg))
                    .exchange((req, resp) -> {
                        HttpStatusCode code = resp.getStatusCode();
                        if (!code.is2xxSuccessful()) {
                            String body = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw new DownstreamServiceException(
                                    "ElevenLabs TTS failed: HTTP " + code.value() + " - " + body);
                        }
                        try (InputStream in = resp.getBody()) {
                            byte[] buf = new byte[TWILIO_FRAME_BYTES];
                            int filled = 0;
                            int n;
                            while ((n = in.read(buf, filled, buf.length - filled)) != -1) {
                                filled += n;
                                if (filled == buf.length) {
                                    if (firstChunkAt[0] < 0) firstChunkAt[0] = System.currentTimeMillis();
                                    totalBytes[0] += filled;
                                    onChunk.accept(buf);
                                    buf = new byte[TWILIO_FRAME_BYTES];
                                    filled = 0;
                                }
                            }
                            if (filled > 0) {
                                byte[] tail = new byte[filled];
                                System.arraycopy(buf, 0, tail, 0, filled);
                                if (firstChunkAt[0] < 0) firstChunkAt[0] = System.currentTimeMillis();
                                totalBytes[0] += filled;
                                onChunk.accept(tail);
                            }
                        } catch (IOException ex) {
                            throw new DownstreamServiceException("ElevenLabs TTS stream read failed", ex);
                        }
                        return null;
                    });
            log.debug("[elevenlabs] tts voice={} chars={} bytes={} ttFirstByteMs={} totalMs={}",
                    voiceId, text.length(), totalBytes[0],
                    firstChunkAt[0] < 0 ? -1 : (firstChunkAt[0] - start),
                    System.currentTimeMillis() - start);
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("ElevenLabs TTS failed: " + ex.getMessage(), ex);
        }
    }

    private static Map<String, Object> buildBody(String text, String modelId, ServiceConfiguration.ElevenLabs cfg) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("model_id", modelId);

        Map<String, Object> voiceSettings = new HashMap<>();
        if (cfg.getTtsStability() != null) voiceSettings.put("stability", cfg.getTtsStability());
        if (cfg.getTtsSimilarityBoost() != null) voiceSettings.put("similarity_boost", cfg.getTtsSimilarityBoost());
        if (cfg.getTtsStyle() != null) voiceSettings.put("style", cfg.getTtsStyle());
        if (cfg.getTtsUseSpeakerBoost() != null) voiceSettings.put("use_speaker_boost", cfg.getTtsUseSpeakerBoost());
        if (!voiceSettings.isEmpty()) body.put("voice_settings", voiceSettings);

        return body;
    }
}
