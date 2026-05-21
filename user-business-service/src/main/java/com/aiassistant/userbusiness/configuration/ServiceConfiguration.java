package com.aiassistant.userbusiness.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "configs")
public class ServiceConfiguration {

    private BusinessDb businessDb;

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
}
