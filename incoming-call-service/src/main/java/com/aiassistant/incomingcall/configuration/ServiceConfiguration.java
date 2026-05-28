package com.aiassistant.incomingcall.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "configs")
public class ServiceConfiguration {

    private UserBusinessService userBusinessService;
    private AuthService authService;
    private CallOrchestration callOrchestration;
    private TwilioPreRoll twilioPreRoll;
    private EnableXApi enablexApi;

    @Data
    public static class UserBusinessService {
        private String baseUrl;
    }

    @Data
    public static class AuthService {
        private String baseUrl;
        private String audience;
        private String scopes;
    }

    @Data
    public static class CallOrchestration {
        private String wsBaseUrl;
    }

    /**
     * Pre-roll message Twilio plays before connecting the Media Stream.
     * Buys call-orchestration-service time to load knowledge + open the AI WS
     * so the bot's first audio lands immediately after the stream opens.
     */
    @Data
    public static class TwilioPreRoll {
        private boolean enabled = true;
        private String text;
        private String voice;
        private String language;
    }

    @Data
    public static class EnableXApi {
        private String baseUrl = "https://api.enablex.io";
    }

}
