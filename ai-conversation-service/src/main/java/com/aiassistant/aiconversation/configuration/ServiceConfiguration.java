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
    private Prompts prompts;
    private BusinessDb businessDb;

    @Data
    public static class AuthService {
        private String baseUrl;
    }

    @Data
    public static class BusinessDb {
        private String name;
        private String schema;
        private String url;
        private Pool pool;
    }

    @Data
    public static class Pool {
        private int maximumPoolSize;
        private int minimumIdle;
        private long maxLifetimeMs;
        private long idleTimeoutMs;
        private long keepaliveTimeMs;
        private String connectionTestQuery;
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
        private int promptCacheTtlSeconds = 3600;
        private int promptCacheRefreshThresholdSeconds = 900;
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

    /**
     * External prompt file paths. When blank, the bundled classpath default is used.
     * When set, the file is read from disk at startup so the prompt can be edited
     * in production without rebuilding the service.
     */
    @Data
    public static class Prompts {
        /** Filesystem path for the conversation system prompt. */
        private String systemPromptPath;
    }
}