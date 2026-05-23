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

/**
 * Lightweight fire-and-forget trigger to conversation-summary-service.
 * We post only the {@code callLogId}; the summary service fetches the
 * transcript itself via a callback to call-orch, runs the LLM on a
 * background pool, and persists into its own {@code call_summaries} table.
 *
 * <p>This client awaits only the 202 ACCEPTED ack — it never blocks on
 * the LLM round-trip.
 */
@Component
public class ConversationSummaryServiceClient {

    private static final String AUDIENCE = "conversation-summary-service";
    private static final List<String> SCOPES = List.of("summary.internal.invoke");

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryServiceClient.class);

    private final RestClient restClient;
    private final ServiceTokenClient serviceTokenClient;

    public ConversationSummaryServiceClient(
            @Qualifier("conversationSummaryServiceRestClient") RestClient conversationSummaryServiceRestClient,
            ServiceTokenClient serviceTokenClient) {
        this.restClient = conversationSummaryServiceRestClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public void trigger(String callLogId) {
        try {
            log.info("Triggering summary callLogId={}", callLogId);
            restClient.post()
                    .uri("/api/internal/summary/trigger")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceTokenClient.getToken(AUDIENCE, SCOPES))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("callLogId", callLogId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new DownstreamServiceException(
                    "Failed to trigger conversation-summary-service: " + ex.getMessage(), ex);
        }
    }
}
