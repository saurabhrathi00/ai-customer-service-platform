package com.aiassistant.callorchestration.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final ServiceConfiguration serviceConfiguration;
    private final SecretsConfiguration secretsConfiguration;

    @Bean(name = "authServiceRestClient")
    public RestClient authServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getAuthService().getBaseUrl())
                .build();
    }

    @Bean(name = "enablexRestClient")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "secrets.enablex", name = "appId")
    public RestClient enablexRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getEnablexApi().getBaseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(
                        secretsConfiguration.getEnablex().getAppId(),
                        secretsConfiguration.getEnablex().getAppKey()))
                .build();
    }

    @Bean(name = "userBusinessServiceRestClient")
    public RestClient userBusinessServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getUserBusinessService().getBaseUrl())
                .build();
    }

    @Bean(name = "knowledgeServiceRestClient")
    public RestClient knowledgeServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getKnowledgeService().getBaseUrl())
                .build();
    }

    @Bean(name = "conversationSummaryServiceRestClient")
    public RestClient conversationSummaryServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getConversationSummaryService().getBaseUrl())
                .build();
    }

    @Bean(name = "notificationServiceRestClient")
    public RestClient notificationServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getNotificationService().getBaseUrl())
                .build();
    }

    @Bean(name = "subscriptionServiceRestClient")
    public RestClient subscriptionServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getSubscriptionService().getBaseUrl())
                .build();
    }
}
