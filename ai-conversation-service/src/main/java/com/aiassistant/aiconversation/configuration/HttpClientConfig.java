package com.aiassistant.aiconversation.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(ServiceConfiguration cfg) {
        int timeoutSec = cfg.getLlm() != null && cfg.getLlm().getStreamingTimeoutSeconds() > 0
                ? cfg.getLlm().getStreamingTimeoutSeconds() : 60;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSec));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024));
    }
}
