package com.aiassistant.callorchestration.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final ServiceConfiguration serviceConfiguration;

    @Bean(name = "authServiceRestClient")
    public RestClient authServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getAuthService().getBaseUrl())
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
}
