package com.aiassistant.aiconversation.llm.groq;

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
import java.util.ArrayList;
import java.util.List;

@Component
public class GroqLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmProvider.class);
    private static final String ID = "groq";
    private static final String DEFAULT_BASE = "https://api.groq.com/openai/v1";

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final SecretsConfiguration.Groq creds;
    private final ServiceConfiguration.Llm llmCfg;

    public GroqLlmProvider(WebClient.Builder builder,
                           ObjectMapper mapper,
                           SecretsConfiguration secrets,
                           ServiceConfiguration serviceCfg) {
        this.mapper = mapper;
        this.creds = secrets.getLlm() == null ? null : secrets.getLlm().getGroq();
        this.llmCfg = serviceCfg.getLlm();
        String baseUrl = (creds == null || creds.getBaseUrl() == null || creds.getBaseUrl().isBlank())
                ? DEFAULT_BASE : creds.getBaseUrl();
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public String id() { return ID; }

    @Override
    public Flux<LlmDelta> streamReply(LlmRequest request) {
        requireConfigured();
        ObjectNode body = buildBody(request);
        body.put("stream", true);
        StreamState state = new StreamState();

        ParameterizedTypeReference<ServerSentEvent<String>> sseType =
                new ParameterizedTypeReference<>() {};

        return webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creds.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(sseType)
                .timeout(Duration.ofSeconds(llmCfg.getStreamingTimeoutSeconds() > 0
                        ? llmCfg.getStreamingTimeoutSeconds() : 60))
                .concatMapIterable(sse -> parseSse(sse, state))
                .onErrorMap(t -> t instanceof LlmException ? t
                        : new LlmException("LLM_TRANSIENT", "Groq stream failed: " + t.getMessage(), t));
    }

    @Override
    public LlmReply complete(LlmRequest request) {
        requireConfigured();
        ObjectNode body = buildBody(request);

        try {
            JsonNode resp = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + creds.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(llmCfg.getRequestTimeoutSeconds() > 0
                            ? llmCfg.getRequestTimeoutSeconds() : 20))
                    .block();
            if (resp == null) {
                throw new LlmException("LLM_TRANSIENT", "Empty Groq response");
            }
            JsonNode choice = resp.path("choices").path(0);
            String text = choice.path("message").path("content").asText("");
            String finish = choice.path("finish_reason").asText(null);
            return LlmReply.builder()
                    .text(text)
                    .finishReason(finish)
                    .usage(parseUsage(resp.path("usage")))
                    .build();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM_TRANSIENT", "Groq call failed: " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (creds == null || creds.getApiKey() == null || creds.getApiKey().isBlank()) {
            throw new LlmException("PROVIDER_NOT_CONFIGURED", "Groq API key not configured");
        }
    }

    private String modelOf(LlmRequest req) {
        if (req.getModelOverride() != null && !req.getModelOverride().isBlank()) {
            return req.getModelOverride();
        }
        if (creds.getModel() == null || creds.getModel().isBlank()) {
            throw new LlmException("PROVIDER_NOT_CONFIGURED", "Groq model not configured (secrets.llm.groq.model)");
        }
        return creds.getModel();
    }

    private ObjectNode buildBody(LlmRequest req) {
        ObjectNode body = mapper.createObjectNode();
        String model = modelOf(req);
        log.info("[groq] using model={} credsModel={}", model, creds == null ? "null" : creds.getModel());
        body.put("model", model);
        body.put("max_tokens", req.getMaxOutputTokens() != null ? req.getMaxOutputTokens()
                : llmCfg.getMaxOutputTokens());
        body.put("temperature", req.getTemperature() != null ? req.getTemperature()
                : llmCfg.getTemperature());

        ArrayNode messages = body.putArray("messages");

        if (req.getSystemPrompt() != null && !req.getSystemPrompt().isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", req.getSystemPrompt());
        }

        if (req.getMessages() != null) {
            for (LlmMessage m : req.getMessages()) {
                ObjectNode msg = messages.addObject();
                msg.put("role", m.getRole() == LlmMessage.Role.ASSISTANT ? "assistant" : "user");
                msg.put("content", m.getContent() == null ? "" : m.getContent());
            }
        }

        return body;
    }

    private List<LlmDelta> parseSse(ServerSentEvent<String> sse, StreamState state) {
        String data = sse.data();
        if (data == null || data.isEmpty() || "[DONE]".equals(data)) {
            if ("[DONE]".equals(data) && !state.finished) {
                state.finished = true;
                return List.of(LlmDelta.builder()
                        .done(true)
                        .finishReason(state.finishReason)
                        .usage(state.lastUsage != null ? state.lastUsage : TokenUsage.zero())
                        .build());
            }
            return List.of();
        }
        List<LlmDelta> out = new ArrayList<>(2);
        try {
            JsonNode node = mapper.readTree(data);
            JsonNode choice = node.path("choices").path(0);
            JsonNode delta = choice.path("delta");

            String text = delta.path("content").asText(null);
            if (text != null && !text.isEmpty()) {
                out.add(LlmDelta.builder().text(text).done(false).build());
            }

            String finish = choice.path("finish_reason").asText(null);
            if (finish != null && !finish.isBlank()) {
                state.finishReason = finish;
            }

            JsonNode usage = node.path("x_groq").path("usage");
            if (!usage.isMissingNode()) {
                state.lastUsage = parseUsage(usage);
            }
            JsonNode topUsage = node.path("usage");
            if (!topUsage.isMissingNode() && topUsage.has("total_tokens")) {
                state.lastUsage = parseUsage(topUsage);
            }
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Groq SSE: {}", e.getMessage());
        }
        return out;
    }

    private TokenUsage parseUsage(JsonNode u) {
        if (u == null || u.isMissingNode()) return TokenUsage.zero();
        return TokenUsage.builder()
                .inputTokens(u.path("prompt_tokens").asLong(0))
                .outputTokens(u.path("completion_tokens").asLong(0))
                .cacheReadInputTokens(0)
                .cacheCreationInputTokens(0)
                .build();
    }

    private static final class StreamState {
        volatile TokenUsage lastUsage;
        volatile String finishReason;
        volatile boolean finished;
    }
}
