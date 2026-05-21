package com.aiassistant.knowledge.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "configs")
public class ServiceConfiguration {

    private BusinessDb businessDb;
    private AuthService authService;
    private UserBusinessService userBusinessService;
    private Cache cache;

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
    public static class AuthService {
        private String baseUrl;
    }

    @Data
    public static class UserBusinessService {
        private String baseUrl;
    }

    @Data
    public static class Cache {
        private Rendered rendered;
    }

    @Data
    public static class Rendered {
        private long ttlMinutes;
        private long maxEntries;
    }
}
