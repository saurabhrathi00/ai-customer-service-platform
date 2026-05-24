package com.aiassistant.notification.clients;

import com.aiassistant.notification.exceptions.DownstreamServiceException;
import com.aiassistant.notification.models.dto.LeadDto;
import com.aiassistant.notification.security.ServiceTokenClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

/** Thin client over user-business-service's internal lead endpoints. */
@Component
public class UserBusinessLeadClient {

    private static final String AUDIENCE = "user-business-service";
    private static final List<String> READ_SCOPES = List.of("leads.internal.read");
    private static final List<String> WRITE_SCOPES = List.of("leads.internal.write");
    private static final ParameterizedTypeReference<List<LeadDto>> LIST_OF_LEADS =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final ServiceTokenClient serviceTokenClient;

    public UserBusinessLeadClient(
            @Qualifier("userBusinessServiceRestClient") RestClient userBusinessServiceRestClient,
            ServiceTokenClient serviceTokenClient) {
        this.restClient = userBusinessServiceRestClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public List<LeadDto> pendingOwnerNotifications() {
        return getList("/api/internal/leads/pending-owner-notifications");
    }

    public List<LeadDto> pendingCustomerNotifications() {
        return getList("/api/internal/leads/pending-customer-notifications");
    }

    public List<LeadDto> dueReminders() {
        return getList("/api/internal/leads/due-reminders");
    }

    public void markOwnerNotified(String leadId) {
        post("/api/internal/leads/" + leadId + "/owner-notified");
    }

    public void markCustomerNotified(String leadId) {
        post("/api/internal/leads/" + leadId + "/customer-notified");
    }

    public void markReminderSent(String leadId) {
        post("/api/internal/leads/" + leadId + "/reminder-sent");
    }

    private List<LeadDto> getList(String path) {
        try {
            List<LeadDto> out = restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceTokenClient.getToken(AUDIENCE, READ_SCOPES))
                    .retrieve()
                    .body(LIST_OF_LEADS);
            return out == null ? Collections.emptyList() : out;
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("GET " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    private void post(String path) {
        try {
            restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceTokenClient.getToken(AUDIENCE, WRITE_SCOPES))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("POST " + path + " failed: " + ex.getMessage(), ex);
        }
    }
}
