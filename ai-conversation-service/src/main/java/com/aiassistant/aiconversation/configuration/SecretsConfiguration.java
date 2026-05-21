package com.aiassistant.aiconversation.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "secrets")
public class SecretsConfiguration {

    private Jwt jwt;
    private AuthService authService;
    private Llm llm;

    @Data
    public static class Jwt {
        private String secret;
        private String type;
        private String expectedAudience;
    }

    @Data
    public static class AuthService {
        private String clientId;
        private String clientSecret;
    }

    @Data
    public static class Llm {
        private Anthropic anthropic;
        private Gemini gemini;
        private Map<String, Map<String, String>> extra;
    }

    @Data
    public static class Anthropic {
        private String apiKey;
        private String model;
        private String baseUrl;
        private String version;
    }

    @Data
    public static class Gemini {
        private String apiKey;
        private String model;
        private String baseUrl;
    }
}