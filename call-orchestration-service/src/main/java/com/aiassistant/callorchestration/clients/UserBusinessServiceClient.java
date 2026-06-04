package com.aiassistant.callorchestration.clients;

import com.aiassistant.callorchestration.exceptions.DownstreamServiceException;
import com.aiassistant.callorchestration.security.ServiceTokenClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class UserBusinessServiceClient {

    private static final String AUDIENCE = "user-business-service";
    private static final List<String> SCOPES = List.of("business.internal.read", "business.internal.write");

    private static final Logger log = LoggerFactory.getLogger(UserBusinessServiceClient.class);

    private final RestClient restClient;
    private final ServiceTokenClient serviceTokenClient;

    public UserBusinessServiceClient(@Qualifier("userBusinessServiceRestClient") RestClient restClient,
                                     ServiceTokenClient serviceTokenClient) {
        this.restClient = restClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public DemoTimeResponse getDemoTime(String businessId) {
        try {
            return restClient.get()
                    .uri("/api/internal/business/{id}/demo-time", businessId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenClient.getToken(AUDIENCE, SCOPES))
                    .retrieve()
                    .body(DemoTimeResponse.class);
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("Failed to get demo time from user-business-service", ex);
        }
    }

    public DemoTimeResponse decrementDemoTime(String businessId, int seconds) {
        try {
            return restClient.post()
                    .uri("/api/internal/business/{id}/demo-time/decrement?seconds={seconds}", businessId, seconds)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenClient.getToken(AUDIENCE, SCOPES))
                    .retrieve()
                    .body(DemoTimeResponse.class);
        } catch (RestClientException ex) {
            log.warn("Failed to decrement demo time businessId={} seconds={}: {}", businessId, seconds, ex.getMessage());
            return null;
        }
    }

    public record DemoTimeResponse(String businessId, int secondsRemaining) {}
}
