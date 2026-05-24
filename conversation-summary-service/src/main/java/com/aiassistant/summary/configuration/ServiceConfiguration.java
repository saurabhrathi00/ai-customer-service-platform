package com.aiassistant.summary.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "configs")
public class ServiceConfiguration {

    private AuthService authService;
    private CallOrchestrationService callOrchestrationService;
    private UserBusinessService userBusinessService;
    private BusinessDb businessDb;
    private Llm llm;
    private Summary summary;

    @Data
    public static class AuthService {
        private String baseUrl;
    }

    @Data
    public static class CallOrchestrationService {
        private String baseUrl;
    }

    @Data
    public static class UserBusinessService {
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
        private int stallTimeoutSeconds;
        private int retryMaxAttempts;
        private long retryInitialBackoffMs;
    }

    @Data
    public static class Summary {
        private int maxOutputTokens;
        private double temperature;
    }
}
