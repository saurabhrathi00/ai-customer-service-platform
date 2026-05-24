package com.aiassistant.summary.clients;

import com.aiassistant.summary.exceptions.DownstreamServiceException;
import com.aiassistant.summary.models.request.CreateLeadRequest;
import com.aiassistant.summary.security.ServiceTokenClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Posts an extracted lead to user-business-service. Idempotent on the
 * receiving side by {@code callLogId} — retries are safe.
 */
@Component
public class UserBusinessLeadClient {

    private static final Logger log = LoggerFactory.getLogger(UserBusinessLeadClient.class);
    private static final String AUDIENCE = "user-business-service";
    private static final List<String> SCOPES = List.of("leads.internal.write");

    private final RestClient restClient;
    private final ServiceTokenClient serviceTokenClient;

    public UserBusinessLeadClient(
            @Qualifier("userBusinessServiceRestClient") RestClient userBusinessServiceRestClient,
            ServiceTokenClient serviceTokenClient) {
        this.restClient = userBusinessServiceRestClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public void createLead(CreateLeadRequest req) {
        try {
            restClient.post()
                    .uri("/api/internal/leads")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceTokenClient.getToken(AUDIENCE, SCOPES))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[lead] posted to user-business-service callLogId={} businessId={} type={}",
                    req.getCallLogId(), req.getBusinessId(), req.getLeadType());
        } catch (RestClientException ex) {
            throw new DownstreamServiceException(
                    "Failed to create lead in user-business-service: " + ex.getMessage(), ex);
        }
    }
}
