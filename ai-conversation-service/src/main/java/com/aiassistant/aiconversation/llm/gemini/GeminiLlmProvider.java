package com.aiassistant.aiconversation.llm.gemini;

import com.aiassistant.aiconversation.configuration.SecretsConfiguration;
import com.aiassistant.aiconversation.configuration.ServiceConfiguration;
import com.aiassistant.aiconversation.exceptions.LlmException;
import com.aiassistant.aiconversation.llm.LlmDelta;
import com.aiassistant.aiconversation.llm.LlmMessage;
import com.aiassistant.aiconversation.llm.LlmProvider;
import com.aiassistant.aiconversation.llm.LlmReply;
import com.aiassistant.aiconversation.llm.LlmRequest;
import com.aiassistant.aiconversation.llm.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Component
public class GeminiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmProvider.class);
    private static final String ID = "gemini";
    private static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com";

    private final WebClient aiStudioClient;
    private final WebClient vertexClient;
    private final ObjectMapper mapper;
    private final SecretsConfiguration.Gemini creds;
    private final ServiceConfiguration.Llm llmCfg;
    private final VertexAiTokenProvider vertex;

    public GeminiLlmProvider(WebClient.Builder builder,
                             ObjectMapper mapper,
                             SecretsConfiguration secrets,
                             ServiceConfiguration serviceCfg,
                             VertexAiTokenProvider vertex) {
        this.mapper = mapper;
        this.creds = secrets.getLlm() == null ? null : secrets.getLlm().getGemini();
        this.llmCfg = serviceCfg.getLlm();
        this.vertex = vertex;
        String aiStudioBase = (creds == null || creds.getBaseUrl() == null || creds.getBaseUrl().isBlank())
                ? DEFAULT_BASE : creds.getBaseUrl();
        this.aiStudioClient = builder.baseUrl(aiStudioBase).build();
        this.vertexClient = vertex.isEnabled() ? builder.baseUrl(vertex.baseUrl()).build() : null;
    }

    @Override public String id() { return ID; }

    @Override
    public Flux<LlmDelta> streamReply(LlmRequest request) {
        requireConfigured();
        String model = modelOf(request);
        ObjectNode body = buildBody(request);
        StreamState state = new StreamState();

        ParameterizedTypeReference<ServerSentEvent<String>> sseType =
                new ParameterizedTypeReference<>() {};

        WebClient client = vertex.isEnabled() ? vertexClient : aiStudioClient;
        return client.post()
                .uri(b -> {
                    if (vertex.isEnabled()) {
                        return b.path("/v1/projects/{p}/locations/{r}/publishers/google/models/{m}:streamGenerateContent")
                                .queryParam("alt", "sse")
                                .build(vertex.projectId(), vertex.region(), model);
                    }
                    return b.path("/v1beta/models/{model}:streamGenerateContent")
                            .queryParam("alt", "sse")
                            .queryParam("key", creds.getApiKey())
                            .build(model);
                })
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .headers(h -> { if (vertex.isEnabled()) h.setBearerAuth(vertex.accessToken()); })
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(sseType)
                .timeout(Duration.ofSeconds(llmCfg.getStreamingTimeoutSeconds() > 0
                        ? llmCfg.getStreamingTimeoutSeconds() : 60))
                .concatMapIterable(sse -> parseSse(sse, state))
                .onErrorMap(t -> t instanceof LlmException ? t
                        : new LlmException("LLM_TRANSIENT", "Gemini stream failed: " + t.getMessage(), t));
    }

    @Override
    public LlmReply complete(LlmRequest request) {
        requireConfigured();
        String model = modelOf(request);
        ObjectNode body = buildBody(request);

        try {
            WebClient client = vertex.isEnabled() ? vertexClient : aiStudioClient;
            JsonNode resp = client.post()
                    .uri(b -> {
                        if (vertex.isEnabled()) {
                            return b.path("/v1/projects/{p}/locations/{r}/publishers/google/models/{m}:generateContent")
                                    .build(vertex.projectId(), vertex.region(), model);
                        }
                        return b.path("/v1beta/models/{model}:generateContent")
                                .queryParam("key", creds.getApiKey())
                                .build(model);
                    })
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .headers(h -> { if (vertex.isEnabled()) h.setBearerAuth(vertex.accessToken()); })
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(llmCfg.getRequestTimeoutSeconds() > 0
                            ? llmCfg.getRequestTimeoutSeconds() : 20))
                    .block();
            if (resp == null) {
                throw new LlmException("LLM_TRANSIENT", "Empty Gemini response");
            }
            JsonNode candidate = resp.path("candidates").path(0);
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                text.append(part.path("text").asText(""));
            }
            String finish = candidate.path("finishReason").asText(null);
            return LlmReply.builder()
                    .text(text.toString())
                    .finishReason(finish)
                    .usage(parseUsage(resp.path("usageMetadata")))
                    .build();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM_TRANSIENT", "Gemini call failed: " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (vertex.isEnabled()) return;
        if (creds == null || creds.getApiKey() == null || creds.getApiKey().isBlank()) {
            throw new LlmException("PROVIDER_NOT_CONFIGURED", "Gemini API key not configured");
        }
    }

    private String modelOf(LlmRequest req) {
        if (req.getModelOverride() != null && !req.getModelOverride().isBlank()) {
            return req.getModelOverride();
        }
        if (creds.getModel() == null || creds.getModel().isBlank()) {
            return "gemini-2.5-flash";
        }
        return creds.getModel();
    }

    private ObjectNode buildBody(LlmRequest req) {
        ObjectNode body = mapper.createObjectNode();

        if (req.getCachedContentName() != null && !req.getCachedContentName().isBlank()) {
            body.put("cachedContent", req.getCachedContentName());
        } else if (req.getSystemPrompt() != null && !req.getSystemPrompt().isBlank()) {
            ObjectNode sys = body.putObject("system_instruction");
            sys.putArray("parts").addObject().put("text", req.getSystemPrompt());
        }

        ArrayNode contents = body.putArray("contents");
        if (req.getMessages() != null) {
            for (LlmMessage m : req.getMessages()) {
                ObjectNode mo = contents.addObject();
                mo.put("role", m.getRole() == LlmMessage.Role.ASSISTANT ? "model" : "user");
                mo.putArray("parts").addObject()
                        .put("text", m.getContent() == null ? "" : m.getContent());
            }
        }

        ObjectNode gc = body.putObject("generationConfig");
        gc.put("maxOutputTokens", req.getMaxOutputTokens() != null ? req.getMaxOutputTokens()
                : llmCfg.getMaxOutputTokens());
        gc.put("temperature", req.getTemperature() != null ? req.getTemperature()
                : llmCfg.getTemperature());
        return body;
    }

    private java.util.List<LlmDelta> parseSse(ServerSentEvent<String> sse, StreamState state) {
        String data = sse.data();
        if (data == null || data.isEmpty()) return java.util.List.of();
        java.util.List<LlmDelta> out = new java.util.ArrayList<>(2);
        try {
            JsonNode node = mapper.readTree(data);
            JsonNode err = node.path("error");
            if (!err.isMissingNode() && !err.isNull()) {
                throw new LlmException("LLM_TRANSIENT",
                        err.path("message").asText("Gemini stream error"));
            }
            JsonNode candidate = node.path("candidates").path(0);

            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                text.append(part.path("text").asText(""));
            }
            if (text.length() > 0) {
                out.add(LlmDelta.builder().text(text.toString()).done(false).build());
            }

            JsonNode usage = node.path("usageMetadata");
            if (!usage.isMissingNode()) state.lastUsage = parseUsage(usage);

            String finish = candidate.path("finishReason").asText(null);
            if (finish != null && !finish.isBlank() && !"FINISH_REASON_UNSPECIFIED".equals(finish)) {
                state.finishReason = finish;
                out.add(LlmDelta.builder()
                        .done(true)
                        .finishReason(finish)
                        .usage(state.lastUsage != null ? state.lastUsage : TokenUsage.zero())
                        .build());
            }
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Gemini SSE: {}", e.getMessage());
        }
        return out;
    }

    private TokenUsage parseUsage(JsonNode u) {
        if (u == null || u.isMissingNode()) return TokenUsage.zero();
        long input = u.path("promptTokenCount").asLong(0);
        long output = u.path("candidatesTokenCount").asLong(0);
        long cached = u.path("cachedContentTokenCount").asLong(0);
        return TokenUsage.builder()
                .inputTokens(input)
                .outputTokens(output)
                .cacheReadInputTokens(cached)
                .cacheCreationInputTokens(0)
                .build();
    }

    private static final class StreamState {
        volatile TokenUsage lastUsage;
        volatile String finishReason;
    }
}