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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationServiceClient {

    private static final String AUDIENCE = "notification-service";
    private static final List<String> SCOPES = List.of("notify.internal.invoke");

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClient.class);

    private final RestClient notificationServiceRestClient;
    private final ServiceTokenClient serviceTokenClient;

    public NotificationServiceClient(@Qualifier("notificationServiceRestClient") RestClient notificationServiceRestClient,
                                     ServiceTokenClient serviceTokenClient) {
        this.notificationServiceRestClient = notificationServiceRestClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public void notifyCallback(String businessId, String customerPhone, String summary) {
        try {
            log.info("Sending callback notification business={} phone={}", businessId, customerPhone);
            Map<String, Object> payload = new HashMap<>();
            payload.put("businessId", businessId);
            payload.put("customerPhone", customerPhone);
            payload.put("summary", summary);
            notificationServiceRestClient.post()
                    .uri("/api/internal/notify/callback")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenClient.getToken(AUDIENCE, SCOPES))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("Failed to call notification-service", ex);
        }
    }
}
