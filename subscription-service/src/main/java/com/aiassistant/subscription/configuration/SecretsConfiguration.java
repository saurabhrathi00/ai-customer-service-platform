package com.aiassistant.subscription.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "secrets")
public class SecretsConfiguration {

    private Jwt jwt;
    private Datasource datasource;
    private Razorpay razorpay;
    private AuthService authService;

    @Data
    public static class Jwt {
        private String secret;
        private String type;
    }

    @Data
    public static class Datasource {
        private String username;
        private String password;
        private String driverClassName;
    }

    @Data
    public static class Razorpay {
        private String keyId;
        private String keySecret;
        private String webhookSecret;
    }

    @Data
    public static class AuthService {
        private String clientId;
        private String clientSecret;
    }
}
