package com.aiassistant.auth.configuration;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "configs")
@Data
public class ServiceConfiguration {

    private BusinessDb businessDb;
    private Auth auth;

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
    public static class Auth {
        private boolean enabled;
        private Policy policy;
    }

    @Data
    public static class Policy {
        /**
         * Map<calleeService, PolicyRule>
         */
        private Map<String, PolicyRule> services;
    }

    @Data
    public static class PolicyRule {
        private List<String> scopes;
    }
}
