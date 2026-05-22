package com.aiassistant.callorchestration.configuration;

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
    private Twilio twilio;
    private ElevenLabs elevenlabs;
    private Deepgram deepgram;

    @Data
    public static class Jwt {
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

    @Data
    public static class Twilio {
        private String accountSid;
        private String authToken;
    }

    @Data
    public static class ElevenLabs {
        private String key;
    }

    @Data
    public static class Deepgram {
        private String key;
    }
}
