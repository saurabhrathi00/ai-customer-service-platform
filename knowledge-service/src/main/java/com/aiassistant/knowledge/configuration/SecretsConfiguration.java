package com.aiassistant.knowledge.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "secrets")
public class SecretsConfiguration {

    private Jwt jwt;
    private Datasource datasource;
    private AuthService authService;

    @Data
    public static class Jwt {
        // MUST match auth-service's secrets.jwt.secret (HS512).
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
    public static class AuthService {
        private String clientId;
        private String clientSecret;
    }
}
