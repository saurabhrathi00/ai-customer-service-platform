package com.aiassistant.aiconversation.configuration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class StartupHealthLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupHealthLogger.class);

    private final ServiceConfiguration serviceConfiguration;
    private final SecretsConfiguration secretsConfiguration;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== ai-conversation-service startup connectivity checks ===");
        RestClient client = RestClient.builder().build();
        probeHealth(client, "auth-service", serviceConfiguration.getAuthService().getBaseUrl());
        probeGemini(client);
        log.info("=== startup checks complete ===");
    }

    private void probeHealth(RestClient client, String name, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Startup check — {}: SKIPPED (baseUrl not configured)", name);
            return;
        }
        String url = baseUrl.replaceAll("/+$", "") + "/api/v1/health";
        long start = System.currentTimeMillis();
        try {
            client.get().uri(url).retrieve().body(String.class);
            long ms = System.currentTimeMillis() - start;
            log.info("Startup check — {} ({}): OK ({} ms)", name, url, ms);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("Startup check — {} ({}): FAILED ({} ms) — {}", name, url, ms, e.getMessage());
        }
    }

    /**
     * Lightweight Gemini check — lists models for the API key. Does NOT call
     * generateContent (which would burn tokens). A non-2xx here = bad key or
     * blocked region.
     */
    private void probeGemini(RestClient client) {
        SecretsConfiguration.Gemini gemini = secretsConfiguration.getLlm() == null
                ? null : secretsConfiguration.getLlm().getGemini();
        String apiKey = gemini == null ? null : gemini.getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("CHANGE_ME")) {
            log.error("Startup check — Gemini: FAILED (API key not configured)");
            return;
        }
        String baseUrl = gemini.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://generativelanguage.googleapis.com";
        }
        String url = baseUrl.replaceAll("/+$", "") + "/v1beta/models?key=" + apiKey;
        long start = System.currentTimeMillis();
        try {
            client.get().uri(url).retrieve().body(String.class);
            long ms = System.currentTimeMillis() - start;
            log.info("Startup check — Gemini API (model {}): OK ({} ms)", gemini.getModel(), ms);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("Startup check — Gemini API: FAILED ({} ms) — {}", ms, e.getMessage());
        }
    }
}
