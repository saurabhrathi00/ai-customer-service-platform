package com.aiassistant.callorchestration.clients;

import com.aiassistant.callorchestration.exceptions.DownstreamServiceException;
import com.aiassistant.callorchestration.security.ServiceTokenClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class SubscriptionServiceClient {

    private static final String AUDIENCE = "subscription-service";
    private static final List<String> SCOPES = List.of("subscription.internal.write");

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceClient.class);

    private final RestClient restClient;
    private final ServiceTokenClient serviceTokenClient;

    public SubscriptionServiceClient(
            @Qualifier("subscriptionServiceRestClient") RestClient subscriptionServiceRestClient,
            ServiceTokenClient serviceTokenClient) {
        this.restClient = subscriptionServiceRestClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public void recordUsage(String businessId, String callId, int callDurationSecs) {
        try {
            log.info("Recording usage businessId={} callId={} durationSecs={}", businessId, callId, callDurationSecs);
            restClient.post()
                    .uri("/api/internal/subscriptions/usage")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceTokenClient.getToken(AUDIENCE, SCOPES))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "businessId", businessId,
                            "callId", callId,
                            "callDurationSecs", callDurationSecs))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new DownstreamServiceException(
                    "Failed to record usage in subscription-service: " + ex.getMessage(), ex);
        }
    }
}
