package com.aiassistant.aiconversation.llm.anthropic;

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
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AnthropicLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmProvider.class);
    private static final String ID = "anthropic";

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final SecretsConfiguration.Anthropic creds;
    private final ServiceConfiguration.Llm llmCfg;

    public AnthropicLlmProvider(WebClient.Builder builder,
                                ObjectMapper mapper,
                                SecretsConfiguration secrets,
                                ServiceConfiguration serviceCfg) {
        this.mapper = mapper;
        this.creds = secrets.getLlm() == null ? null : secrets.getLlm().getAnthropic();
        this.llmCfg = serviceCfg.getLlm();
        String baseUrl = (creds == null || creds.getBaseUrl() == null || creds.getBaseUrl().isBlank())
                ? "https://api.anthropic.com" : creds.getBaseUrl();
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @Override public String id() { return ID; }

    @Override
    public Flux<LlmDelta> streamReply(LlmRequest request) {
        requireConfigured();
        ObjectNode body = buildBody(request, true);
        StreamState state = new StreamState();

        ParameterizedTypeReference<ServerSentEvent<String>> sseType =
                new ParameterizedTypeReference<>() {};

        return webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", creds.getApiKey())
                .header("anthropic-version", anthropicVersion())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(sseType)
                .timeout(Duration.ofSeconds(llmCfg.getStreamingTimeoutSeconds() > 0
                        ? llmCfg.getStreamingTimeoutSeconds() : 60))
                .mapNotNull(sse -> parseSse(sse, state))
                .onErrorMap(t -> t instanceof LlmException ? t
                        : new LlmException("LLM_TRANSIENT", "Anthropic stream failed: " + t.getMessage(), t));
    }

    @Override
    public LlmReply complete(LlmRequest request) {
        requireConfigured();
        ObjectNode body = buildBody(request, false);

        try {
            JsonNode resp = webClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", creds.getApiKey())
                    .header("anthropic-version", anthropicVersion())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(llmCfg.getRequestTimeoutSeconds() > 0
                            ? llmCfg.getRequestTimeoutSeconds() : 20))
                    .block();
            if (resp == null) {
                throw new LlmException("LLM_TRANSIENT", "Empty Anthropic response");
            }
            StringBuilder text = new StringBuilder();
            JsonNode content = resp.path("content");
            if (content.isArray()) {
                for (JsonNode b : content) {
                    if ("text".equals(b.path("type").asText())) {
                        text.append(b.path("text").asText(""));
                    }
                }
            }
            return LlmReply.builder()
                    .text(text.toString())
                    .finishReason(resp.path("stop_reason").asText(null))
                    .usage(parseUsage(resp.path("usage")))
                    .build();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM_TRANSIENT", "Anthropic call failed: " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (creds == null || creds.getApiKey() == null || creds.getApiKey().isBlank()) {
            throw new LlmException("PROVIDER_NOT_CONFIGURED", "Anthropic API key not configured");
        }
    }

    private String anthropicVersion() {
        return (creds.getVersion() == null || creds.getVersion().isBlank())
                ? "2023-06-01" : creds.getVersion();
    }

    private ObjectNode buildBody(LlmRequest req, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        String model = (req.getModelOverride() != null && !req.getModelOverride().isBlank())
                ? req.getModelOverride() : creds.getModel();
        body.put("model", model);
        body.put("max_tokens", req.getMaxOutputTokens() != null ? req.getMaxOutputTokens()
                : llmCfg.getMaxOutputTokens());
        body.put("temperature", req.getTemperature() != null ? req.getTemperature()
                : llmCfg.getTemperature());
        if (stream) body.put("stream", true);

        if (req.getSystemPrompt() != null && !req.getSystemPrompt().isBlank()) {
            boolean cache = Boolean.TRUE.equals(req.getCacheSystemPrompt())
                    || (req.getCacheSystemPrompt() == null && llmCfg.isPromptCacheEnabled());
            if (cache) {
                ArrayNode sys = body.putArray("system");
                ObjectNode block = sys.addObject();
                block.put("type", "text");
                block.put("text", req.getSystemPrompt());
                block.putObject("cache_control").put("type", "ephemeral");
            } else {
                body.put("system", req.getSystemPrompt());
            }
        }

        ArrayNode msgs = body.putArray("messages");
        if (req.getMessages() != null) {
            for (LlmMessage m : req.getMessages()) {
                ObjectNode mo = msgs.addObject();
                mo.put("role", m.getRole() == LlmMessage.Role.USER ? "user" : "assistant");
                mo.put("content", m.getContent() == null ? "" : m.getContent());
            }
        }
        return body;
    }

    private LlmDelta parseSse(ServerSentEvent<String> sse, StreamState state) {
        String evt = sse.event();
        String data = sse.data();
        if (data == null || data.isEmpty()) return null;
        try {
            JsonNode node = mapper.readTree(data);
            String type = node.path("type").asText(evt);
            switch (type) {
                case "message_start" -> {
                    JsonNode usage = node.path("message").path("usage");
                    state.inputTokens.set(usage.path("input_tokens").asLong(0));
                    state.cacheRead.set(usage.path("cache_read_input_tokens").asLong(0));
                    state.cacheCreation.set(usage.path("cache_creation_input_tokens").asLong(0));
                }
                case "content_block_delta" -> {
                    String text = node.path("delta").path("text").asText("");
                    if (!text.isEmpty()) {
                        return LlmDelta.builder().text(text).done(false).build();
                    }
                }
                case "message_delta" -> {
                    state.outputTokens.set(node.path("usage").path("output_tokens").asLong(state.outputTokens.get()));
                    String stop = node.path("delta").path("stop_reason").asText(null);
                    if (stop != null) state.finishReason = stop;
                }
                case "message_stop" -> {
                    return LlmDelta.builder()
                            .done(true)
                            .finishReason(state.finishReason)
                            .usage(state.toUsage())
                            .build();
                }
                case "error" -> {
                    String msg = node.path("error").path("message").asText("Anthropic stream error");
                    throw new LlmException("LLM_TRANSIENT", msg);
                }
                default -> { /* ignore ping, content_block_start, etc. */ }
            }
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse SSE event '{}': {}", evt, e.getMessage());
        }
        return null;
    }

    private TokenUsage parseUsage(JsonNode u) {
        if (u == null || u.isMissingNode()) return TokenUsage.zero();
        return TokenUsage.builder()
                .inputTokens(u.path("input_tokens").asLong(0))
                .outputTokens(u.path("output_tokens").asLong(0))
                .cacheReadInputTokens(u.path("cache_read_input_tokens").asLong(0))
                .cacheCreationInputTokens(u.path("cache_creation_input_tokens").asLong(0))
                .build();
    }

    private static final class StreamState {
        final AtomicLong inputTokens = new AtomicLong();
        final AtomicLong outputTokens = new AtomicLong();
        final AtomicLong cacheRead = new AtomicLong();
        final AtomicLong cacheCreation = new AtomicLong();
        volatile String finishReason;

        TokenUsage toUsage() {
            return TokenUsage.builder()
                    .inputTokens(inputTokens.get())
                    .outputTokens(outputTokens.get())
                    .cacheReadInputTokens(cacheRead.get())
                    .cacheCreationInputTokens(cacheCreation.get())
                    .build();
        }
    }
}
