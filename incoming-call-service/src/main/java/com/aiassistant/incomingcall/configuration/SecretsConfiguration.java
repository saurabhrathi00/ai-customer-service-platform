package com.aiassistant.incomingcall.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "secrets")
public class SecretsConfiguration {

    private Twilio twilio;
    private AuthService authService;

    @Data
    public static class Twilio {
        private String accountSid;
        private String authToken;
    }

    @Data
    public static class AuthService {
        private String clientId;
        private String clientSecret;
    }

}
