package com.aiassistant.aiconversation.llm.gemini;

import com.aiassistant.aiconversation.configuration.SecretsConfiguration;
import com.aiassistant.aiconversation.configuration.ServiceConfiguration;
import com.aiassistant.aiconversation.models.dao.GeminiPromptCacheEntity;
import com.aiassistant.aiconversation.repository.GeminiPromptCacheRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GeminiContextCacheService {

    private static final Logger log = LoggerFactory.getLogger(GeminiContextCacheService.class);
    private static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com";

    private final GeminiPromptCacheRepository cacheRepository;
    private final ObjectMapper mapper;
    private final SecretsConfiguration secrets;
    private final ServiceConfiguration serviceConfiguration;
    private final WebClient.Builder webClientBuilder;
    private final VertexAiTokenProvider vertex;

    private volatile WebClient webClient;
    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

    private WebClient webClient() {
        if (webClient == null) {
            synchronized (this) {
                if (webClient == null) {
                    String base;
                    if (vertex.isEnabled()) {
                        base = vertex.baseUrl();
                    } else {
                        SecretsConfiguration.Gemini creds = geminiCreds();
                        base = (creds == null || creds.getBaseUrl() == null || creds.getBaseUrl().isBlank())
                                ? DEFAULT_BASE : creds.getBaseUrl();
                    }
                    webClient = webClientBuilder.baseUrl(base).build();
                }
            }
        }
        return webClient;
    }

    private SecretsConfiguration.Gemini geminiCreds() {
        return secrets.getLlm() == null ? null : secrets.getLlm().getGemini();
    }

    private String apiKey() {
        SecretsConfiguration.Gemini c = geminiCreds();
        return c == null ? null : c.getApiKey();
    }

    private String model() {
        SecretsConfiguration.Gemini c = geminiCreds();
        if (c == null || c.getModel() == null || c.getModel().isBlank()) return "gemini-2.5-flash";
        return c.getModel();
    }

    public String resolveCache(String businessId, String renderedSystemPrompt) {
        ServiceConfiguration.Llm cfg = serviceConfiguration.getLlm();
        if (!cfg.isPromptCacheEnabled()) return null;
        if (!vertex.isEnabled() && apiKey() == null) return null;

        try {
            String hash = sha256(renderedSystemPrompt);
            String currentModel = model();

            GeminiPromptCacheEntity existing = cacheRepository.findByBusinessId(businessId).orElse(null);

            if (existing != null
                    && existing.getKnowledgeHash().equals(hash)
                    && existing.getModel().equals(currentModel)
                    && existing.getExpireTime().isAfter(Instant.now())) {

                long secondsLeft = Duration.between(Instant.now(), existing.getExpireTime()).getSeconds();
                if (secondsLeft < cfg.getPromptCacheRefreshThresholdSeconds()) {
                    refreshTtlAsync(existing.getGeminiCacheName(), existing.getId(), cfg.getPromptCacheTtlSeconds());
                }
                log.debug("[gemini-cache] HIT businessId={} cacheName={} expiresInSec={}",
                        businessId, existing.getGeminiCacheName(), secondsLeft);
                return existing.getGeminiCacheName();
            }

            // Miss — coalesce concurrent creations
            String cacheKey = businessId + ":" + hash;
            boolean[] creator = {false};
            CompletableFuture<String> future = inFlight.computeIfAbsent(cacheKey, k -> {
                creator[0] = true;
                return new CompletableFuture<>();
            });
            if (creator[0]) {
                try {
                    String name = createCache(businessId, renderedSystemPrompt, hash, currentModel, existing, cfg);
                    future.complete(name);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                } finally {
                    inFlight.remove(cacheKey);
                }
            }
            return future.get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.warn("[gemini-cache] resolveCache failed businessId={}: {}", businessId, e.getMessage());
            return null;
        }
    }

    private String createCache(String businessId, String renderedSystemPrompt, String hash,
                                 String currentModel, GeminiPromptCacheEntity existing,
                                 ServiceConfiguration.Llm cfg) {
        if (existing != null) {
            deleteRemoteCache(existing.getGeminiCacheName());
            cacheRepository.delete(existing);
            cacheRepository.flush();
        }

        ObjectNode body = mapper.createObjectNode();
        // Vertex expects the full resource path for model; AI Studio expects "models/{m}".
        body.put("model", vertex.isEnabled()
                ? String.format("projects/%s/locations/%s/publishers/google/models/%s",
                    vertex.projectId(), vertex.region(), currentModel)
                : "models/" + currentModel);

        ObjectNode sysInstruction = body.putObject("systemInstruction");
        sysInstruction.putArray("parts").addObject().put("text", renderedSystemPrompt);

        body.put("ttl", cfg.getPromptCacheTtlSeconds() + "s");

        log.info("[gemini-cache] CREATING businessId={} model={} ttl={}s vertex={}",
                businessId, currentModel, cfg.getPromptCacheTtlSeconds(), vertex.isEnabled());

        JsonNode resp = webClient().post()
                .uri(b -> {
                    if (vertex.isEnabled()) {
                        return b.path("/v1/projects/{p}/locations/{r}/cachedContents")
                                .build(vertex.projectId(), vertex.region());
                    }
                    return b.path("/v1beta/cachedContents")
                            .queryParam("key", apiKey())
                            .build();
                })
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .headers(h -> { if (vertex.isEnabled()) h.setBearerAuth(vertex.accessToken()); })
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(15))
                .block();

        if (resp == null || !resp.has("name")) {
            log.warn("[gemini-cache] create returned empty response businessId={}", businessId);
            return null;
        }

        String cacheName = resp.path("name").asText();
        Instant expireTime = Instant.parse(resp.path("expireTime").asText());

        GeminiPromptCacheEntity entity = GeminiPromptCacheEntity.builder()
                .businessId(businessId)
                .knowledgeHash(hash)
                .geminiCacheName(cacheName)
                .model(currentModel)
                .expireTime(expireTime)
                .build();
        cacheRepository.save(entity);

        log.info("[gemini-cache] CREATED businessId={} cacheName={} expiresAt={}",
                businessId, cacheName, expireTime);
        return cacheName;
    }

    @Async
    public void refreshTtlAsync(String cacheName, String entityId, int ttlSeconds) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("ttl", ttlSeconds + "s");

            // cacheName comes back from the API in the right shape per provider
            // (AI Studio: "cachedContents/abc"; Vertex: "projects/.../cachedContents/abc"),
            // we just prefix the right API version.
            String prefix = vertex.isEnabled() ? "/v1/" : "/v1beta/";
            JsonNode resp = webClient().patch()
                    .uri(b -> {
                        var u = b.path(prefix + "{cacheName}");
                        if (!vertex.isEnabled()) u = u.queryParam("key", apiKey());
                        return u.build(cacheName);
                    })
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .headers(h -> { if (vertex.isEnabled()) h.setBearerAuth(vertex.accessToken()); })
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (resp != null && resp.has("expireTime")) {
                Instant newExpiry = Instant.parse(resp.path("expireTime").asText());
                cacheRepository.findById(entityId).ifPresent(e -> {
                    e.setExpireTime(newExpiry);
                    cacheRepository.save(e);
                });
                log.info("[gemini-cache] TTL refreshed cacheName={} newExpiry={}", cacheName, newExpiry);
            }
        } catch (Exception e) {
            log.warn("[gemini-cache] TTL refresh failed cacheName={}: {}", cacheName, e.getMessage());
        }
    }

    private void deleteRemoteCache(String cacheName) {
        try {
            String prefix = vertex.isEnabled() ? "/v1/" : "/v1beta/";
            webClient().delete()
                    .uri(b -> {
                        var u = b.path(prefix + "{cacheName}");
                        if (!vertex.isEnabled()) u = u.queryParam("key", apiKey());
                        return u.build(cacheName);
                    })
                    .headers(h -> { if (vertex.isEnabled()) h.setBearerAuth(vertex.accessToken()); })
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .subscribe(
                            ok -> log.debug("[gemini-cache] deleted remote cacheName={}", cacheName),
                            err -> log.debug("[gemini-cache] delete failed (may already be expired) cacheName={}: {}",
                                    cacheName, err.getMessage()));
        } catch (Exception e) {
            log.debug("[gemini-cache] delete fire-and-forget failed cacheName={}", cacheName);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
