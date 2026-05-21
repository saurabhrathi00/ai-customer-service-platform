package com.aiassistant.aiconversation.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "configs")
public class ServiceConfiguration {

    private AuthService authService;
    private Llm llm;
    private Session session;
    private Summary summary;

    @Data
    public static class AuthService {
        private String baseUrl;
    }

    @Data
    public static class Llm {
        private String defaultProvider;
        private int maxOutputTokens;
        private double temperature;
        private int requestTimeoutSeconds;
        private int streamingTimeoutSeconds;
        private int historyWindowTurns;
        private boolean promptCacheEnabled;
        private int stallTimeoutSeconds;
        private int retryMaxAttempts;
        private long retryInitialBackoffMs;
    }

    @Data
    public static class Session {
        private int idleTimeoutMinutes;
        private int maxConcurrent;
        private int sweepIntervalSeconds;
    }

    @Data
    public static class Summary {
        private int maxOutputTokens;
        private double temperature;
    }
}