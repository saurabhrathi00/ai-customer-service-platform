package com.aiassistant.incomingcall.clients;

import com.aiassistant.incomingcall.configuration.ServiceConfiguration;
import com.aiassistant.incomingcall.exceptions.BusinessNotFoundException;
import com.aiassistant.incomingcall.exceptions.DownstreamServiceException;
import com.aiassistant.incomingcall.models.response.BusinessLookupResponse;
import com.aiassistant.incomingcall.security.ServiceTokenClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class UserBusinessClient {

    private static final Logger log = LoggerFactory.getLogger(UserBusinessClient.class);

    private final RestClient userBusinessRestClient;
    private final ServiceTokenClient serviceTokenClient;
    private final ServiceConfiguration serviceConfiguration;

    public UserBusinessClient(@Qualifier("userBusinessRestClient") RestClient userBusinessRestClient,
                              ServiceTokenClient serviceTokenClient,
                              ServiceConfiguration serviceConfiguration) {
        this.userBusinessRestClient = userBusinessRestClient;
        this.serviceTokenClient = serviceTokenClient;
        this.serviceConfiguration = serviceConfiguration;
    }

    public BusinessLookupResponse lookupByPhoneNumber(String phoneNumber) {
        String token = serviceTokenClient.getToken();
        String baseUrl = serviceConfiguration.getUserBusinessService().getBaseUrl();
        URI uri = URI.create(baseUrl
                + "/api/internal/business/lookup?phoneNumber="
                + URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8));
        log.info("Looking up business via {}", uri);
        try {
            return userBusinessRestClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            throw new BusinessNotFoundException(
                                    "No business found for phone number " + phoneNumber);
                        }
                        throw new DownstreamServiceException(
                                "user-business-service returned " + res.getStatusCode());
                    })
                    .body(BusinessLookupResponse.class);
        } catch (BusinessNotFoundException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("user-business-service lookup failed: {}", ex.getMessage());
            throw new DownstreamServiceException("Failed to call user-business-service", ex);
        }
    }
}
