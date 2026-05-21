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

}
