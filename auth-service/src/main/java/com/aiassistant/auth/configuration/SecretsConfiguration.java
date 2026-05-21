package com.aiassistant.auth.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "secrets")
public class SecretsConfiguration {

    private Jwt jwt;
    private Datasource datasource;
    private Map<String, ServiceCredentials> services;

    @Data
    public static class Jwt {
        private String secret;
        private String type;
        private Duration accessTokenExpiration;   // maps from access-token.expiration
        private Duration refreshTokenExpiration;  // maps from refresh-token.expiration
        private Duration serviceTokenExpiration;  // maps from service-token.expiration
    }

    @Data
    public static class Datasource {
        private String username;
        private String password;
        private String driverClassName; // matches driver-class-name
    }

    @Data
    public static class ServiceCredentials {
        private String id;
        private String password;
    }
}


