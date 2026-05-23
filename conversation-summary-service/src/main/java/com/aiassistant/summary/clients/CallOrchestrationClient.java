package com.aiassistant.summary.clients;

import com.aiassistant.summary.exceptions.DownstreamServiceException;
import com.aiassistant.summary.models.response.TranscriptPayload;
import com.aiassistant.summary.security.ServiceTokenClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** Pulls a finalised call's transcript + caller context from
 *  call-orchestration-service. Authenticates with a service token. */
@Component
public class CallOrchestrationClient {

    private static final Logger log = LoggerFactory.getLogger(CallOrchestrationClient.class);
    private static final String AUDIENCE = "call-orchestration-service";
    private static final List<String> SCOPES = List.of("calls.internal.read");

    private final RestClient restClient;
    private final ServiceTokenClient serviceTokenClient;

    public CallOrchestrationClient(
            @Qualifier("callOrchestrationServiceRestClient") RestClient callOrchestrationServiceRestClient,
            ServiceTokenClient serviceTokenClient) {
        this.restClient = callOrchestrationServiceRestClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public TranscriptPayload fetchTranscript(String callLogId) {
        try {
            log.info("Fetching transcript from call-orch callLogId={}", callLogId);
            TranscriptPayload payload = restClient.get()
                    .uri("/api/internal/calls/{id}/transcript", callLogId)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceTokenClient.getToken(AUDIENCE, SCOPES))
                    .retrieve()
                    .body(TranscriptPayload.class);
            if (payload == null) {
                throw new DownstreamServiceException(
                        "call-orch returned empty transcript for callLogId=" + callLogId);
            }
            log.info("Fetched transcript callLogId={} turns={}",
                    callLogId, payload.getHistory() == null ? 0 : payload.getHistory().size());
            return payload;
        } catch (RestClientException ex) {
            throw new DownstreamServiceException(
                    "Failed to fetch transcript from call-orchestration-service: " + ex.getMessage(), ex);
        }
    }
}
