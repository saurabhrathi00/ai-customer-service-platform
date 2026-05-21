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

@Component
public class KnowledgeServiceClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceClient.class);

    private final RestClient knowledgeServiceRestClient;
    private final ServiceTokenClient serviceTokenClient;

    public KnowledgeServiceClient(@Qualifier("knowledgeServiceRestClient") RestClient knowledgeServiceRestClient,
                                  ServiceTokenClient serviceTokenClient) {
        this.knowledgeServiceRestClient = knowledgeServiceRestClient;
        this.serviceTokenClient = serviceTokenClient;
    }

    public String fetchKnowledgeText(String businessId) {
        try {
            log.info("Fetching knowledge for business={}", businessId);
            return knowledgeServiceRestClient.get()
                    .uri("/api/internal/knowledge/{businessId}", businessId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenClient.getToken())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("Failed to call knowledge-service", ex);
        }
    }
}
