package com.aiassistant.incomingcall.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final ServiceConfiguration serviceConfiguration;

    // Twilio retries the webhook after ~15s, so anything slower than that is useless anyway.
    // Tight timeouts keep a slow downstream from stalling the call hand-off.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private ClientHttpRequestFactory timedRequestFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        f.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return f;
    }

    @Bean(name = "authServiceRestClient")
    public RestClient authServiceRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getAuthService().getBaseUrl())
                .requestFactory(timedRequestFactory())
                .build();
    }

    @Bean(name = "userBusinessRestClient")
    public RestClient userBusinessRestClient() {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getUserBusinessService().getBaseUrl())
                .requestFactory(timedRequestFactory())
                .build();
    }

    @Bean(name = "enablexRestClient")
    public RestClient enablexRestClient(SecretsConfiguration secrets) {
        return RestClient.builder()
                .baseUrl(serviceConfiguration.getEnablexApi().getBaseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(
                        secrets.getEnablex().getAppId(),
                        secrets.getEnablex().getAppKey()))
                .requestFactory(timedRequestFactory())
                .build();
    }
}
