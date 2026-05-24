package com.aiassistant.notification.configuration;

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

    @Bean(name = "userBusinessServiceRestClient")
    public RestClient userBusinessServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getUserBusinessService().getBaseUrl())
                .build();
    }

    @Bean(name = "whatsappRestClient")
    public RestClient whatsappRestClient() {
        // Base URL only — the WhatsApp client appends /{phoneNumberId}/messages.
        // Auth header is added per-call from the secret token.
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getWhatsapp().getGraphApiBaseUrl())
                .build();
    }
}
