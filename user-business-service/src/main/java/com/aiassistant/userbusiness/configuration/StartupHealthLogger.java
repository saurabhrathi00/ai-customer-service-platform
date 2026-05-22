package com.aiassistant.userbusiness.configuration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@RequiredArgsConstructor
public class StartupHealthLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupHealthLogger.class);

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== user-business-service startup connectivity checks ===");
        probeDatabase();
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
}
