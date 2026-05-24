package com.aiassistant.notification.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "secrets")
public class SecretsConfiguration {

    private Jwt jwt;
    private AuthService authService;
    private Whatsapp whatsapp;

    @Data
    public static class Jwt {
        /** Shared HS512 secret used to verify inbound service tokens. */
        private String secret;
        private String type;
        private String expectedAudience;
    }

    @Data
    public static class AuthService {
        /** Our identity when requesting outbound service tokens (to call
         *  user-business-service). Matches an entry in
         *  auth-service/secrets/secrets.properties. */
        private String clientId;
        private String clientSecret;
    }

    /** Meta WhatsApp Cloud API credentials. The access token is long-lived
     *  for the system user that owns the WABA. */
    @Data
    public static class Whatsapp {
        private String accessToken;
    }
}
