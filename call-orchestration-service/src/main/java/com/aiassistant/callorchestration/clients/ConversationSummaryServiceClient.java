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

import java.util.Map;

@Component
public class ConversationSummaryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryServiceClient.class);

    private final RestClient conversationSummaryServiceRestClient;
    private final ServiceTokenClient serviceTokenClient;

    public ConversationSummaryServiceClient(@Qualifier("conversationSummaryServiceRestClient") RestClient conversationSummaryServiceRestClient,
                                            ServiceTokenClient serviceTokenClient) {
        this.conversationSummaryServiceRestClient = conversationSummaryServiceRestClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public void requestSummary(String businessId, String callId, String transcript) {
        try {
            log.info("Requesting summary call={}", callId);
            conversationSummaryServiceRestClient.post()
                    .uri("/api/internal/summary/generate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenClient.getToken())
                    .body(Map.of(
                            "businessId", businessId,
                            "callId", callId,
                            "transcript", transcript == null ? "" : transcript))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("Failed to call conversation-summary-service", ex);
        }
    }
}
