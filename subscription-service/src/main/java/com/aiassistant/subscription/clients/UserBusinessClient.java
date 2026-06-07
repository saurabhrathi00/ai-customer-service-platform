package com.aiassistant.subscription.clients;

import com.aiassistant.subscription.configuration.ServiceConfiguration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserBusinessClient {

    private static final Logger log = LoggerFactory.getLogger(UserBusinessClient.class);

    private final RestTemplate restTemplate;
    private final ServiceConfiguration configs;
    private final AuthTokenClient authTokenClient;

    public void updateSubscriptionStatus(String businessId, String status, String subscriptionId) {
        String url = configs.getUserBusinessService().getBaseUrl()
                + "/api/internal/business/" + businessId + "/subscription-status";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(authTokenClient.getServiceToken());

        Map<String, String> body = Map.of(
                "subscriptionStatus", status,
                "subscriptionId", subscriptionId
        );

        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
            log.info("Updated business {} subscription status to {}", businessId, status);
        } catch (Exception e) {
            log.error("Failed to update subscription status for business {}: {}", businessId, e.getMessage());
            throw e;
        }
    }
}
