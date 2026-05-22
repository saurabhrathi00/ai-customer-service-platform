package com.aiassistant.knowledge.configuration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class StartupHealthLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupHealthLogger.class);

    private final DataSource dataSource;
    private final ServiceConfiguration serviceConfiguration;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== knowledge-service startup connectivity checks ===");
        probeDatabase();
        RestClient client = RestClient.builder().build();
        probeHealth(client, "auth-service", serviceConfiguration.getAuthService().getBaseUrl());
        probeHealth(client, "user-business-service", serviceConfiguration.getUserBusinessService().getBaseUrl());
        log.info("=== startup checks complete ===");
    }

    private void probeDatabase() {
        long start = System.currentTimeMillis();
        try (Connection c = dataSource.getConnection()) {
            boolean valid = c.isValid(5);
            long ms = System.currentTimeMillis() - start;
            if (valid) {
                log.info("Startup check — Supabase DB: OK ({} ms)", ms);
            } else {
                log.error("Startup check — Supabase DB: FAILED (connection not valid, {} ms)", ms);
            }
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("Startup check — Supabase DB: FAILED ({} ms) — {}", ms, e.getMessage());
        }
    }

    private void probeHealth(RestClient client, String name, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Startup check — {}: SKIPPED (baseUrl not configured)", name);
            return;
        }
        String url = baseUrl.replaceAll("/+$", "") + "/api/v1/health";
        long start = System.currentTimeMillis();
        try {
            String body = client.get().uri(url).retrieve().body(String.class);
            long ms = System.currentTimeMillis() - start;
            log.info("Startup check — {} ({}): OK ({} ms)", name, url, ms);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("Startup check — {} ({}): FAILED ({} ms) — {}", name, url, ms, e.getMessage());
        }
    }
}
