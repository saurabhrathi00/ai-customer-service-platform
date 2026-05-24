package com.aiassistant.summary.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final ServiceConfiguration serviceConfiguration;

    @Bean
    public WebClient.Builder webClientBuilder() {
        int timeoutSec = serviceConfiguration.getLlm() != null
                && serviceConfiguration.getLlm().getStreamingTimeoutSeconds() > 0
                ? serviceConfiguration.getLlm().getStreamingTimeoutSeconds() : 60;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSec));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024));
    }

    @Bean(name = "authServiceRestClient")
    public RestClient authServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getAuthService().getBaseUrl())
                .build();
    }

    @Bean(name = "callOrchestrationServiceRestClient")
    public RestClient callOrchestrationServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getCallOrchestrationService().getBaseUrl())
                .build();
    }

    @Bean(name = "userBusinessServiceRestClient")
    public RestClient userBusinessServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getUserBusinessService().getBaseUrl())
                .build();
    }
}
