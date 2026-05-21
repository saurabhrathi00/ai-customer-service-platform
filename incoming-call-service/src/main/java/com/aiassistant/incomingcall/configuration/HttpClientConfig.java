package com.aiassistant.incomingcall.configuration;

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

    @Bean(name = "userBusinessRestClient")
    public RestClient userBusinessRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getUserBusinessService().getBaseUrl())
                .build();
    }
}
