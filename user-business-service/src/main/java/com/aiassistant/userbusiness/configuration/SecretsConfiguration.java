package com.aiassistant.userbusiness.configuration;

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
    private Twilio twilio;
    private Map<String, ServiceCredentials> services;

    @Data
    public static class Jwt {
        // MUST match auth-service's secrets.jwt.secret (HS512). This service verifies tokens, it does not issue them.
        private String secret;
        private String type;
        private String expectedAudience;
    }

    @Data
    public static class Datasource {
        private String username;
        private String password;
        private String driverClassName;
    }

    @Data
    public static class Twilio {
        private String accountSid;
        private String authToken;
    }

    @Data
    public static class ServiceCredentials {
        private String id;
        private String password;
    }

}
