package com.aiassistant.incomingcall.configuration;

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
        log.info("=== incoming-call-service startup connectivity checks ===");
        RestClient client = RestClient.builder().build();
        probeHealth(client, "auth-service", serviceConfiguration.getAuthService().getBaseUrl());
        probeHealth(client, "user-business-service", serviceConfiguration.getUserBusinessService().getBaseUrl());
        probeTwilio(client);
        verifyCallOrchestrationUrl();
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

    private void probeTwilio(RestClient client) {
        SecretsConfiguration.Twilio twilio = secretsConfiguration.getTwilio();
        if (twilio == null || isBlankOrPlaceholder(twilio.getAccountSid()) || isBlankOrPlaceholder(twilio.getAuthToken())) {
            log.error("Startup check — Twilio: FAILED (accountSid/authToken not configured)");
            return;
        }
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilio.getAccountSid() + ".json";
        long start = System.currentTimeMillis();
        try {
            client.get()
                    .uri(url)
                    .header("Authorization", "Basic " + java.util.Base64.getEncoder()
                            .encodeToString((twilio.getAccountSid() + ":" + twilio.getAuthToken()).getBytes()))
                    .retrieve()
                    .body(String.class);
            long ms = System.currentTimeMillis() - start;
            log.info("Startup check — Twilio API (account {}): OK ({} ms)", twilio.getAccountSid(), ms);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("Startup check — Twilio API: FAILED ({} ms) — {}", ms, e.getMessage());
        }
    }

    private void verifyCallOrchestrationUrl() {
        String url = serviceConfiguration.getCallOrchestration() == null
                ? null : serviceConfiguration.getCallOrchestration().getWsBaseUrl();
        if (url == null || url.isBlank()) {
            log.error("Startup check — call-orchestration ws URL: FAILED (not configured)");
        } else if (url.contains("CHANGE_ME")) {
            log.error("Startup check — call-orchestration ws URL: FAILED (placeholder value '{}'); set CONFIGS_CALLORCHESTRATION_WSBASEURL", url);
        } else {
            log.info("Startup check — call-orchestration ws URL: configured ({})", url);
        }
    }

    private static boolean isBlankOrPlaceholder(String s) {
        return s == null || s.isBlank() || s.startsWith("CHANGE_ME") || s.startsWith("...");
    }
}
