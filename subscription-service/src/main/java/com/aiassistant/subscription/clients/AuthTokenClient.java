package com.aiassistant.subscription.clients;

import com.aiassistant.subscription.configuration.SecretsConfiguration;
import com.aiassistant.subscription.configuration.ServiceConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AuthTokenClient {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenClient.class);

    private final RestTemplate restTemplate;
    private final ServiceConfiguration configs;
    private final SecretsConfiguration secrets;

    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public synchronized String getServiceToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }

        String url = configs.getAuthService().getBaseUrl() + "/api/v1/auth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", secrets.getAuthService().getClientId());
        body.add("client_secret", secrets.getAuthService().getClientSecret());

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);

            JsonNode responseBody = response.getBody();
            cachedToken = responseBody.path("access_token").asText();
            int expiresIn = responseBody.path("expires_in").asInt(3600);
            tokenExpiry = Instant.now().plusSeconds(expiresIn);

            log.debug("Service token obtained, expires in {}s", expiresIn);
            return cachedToken;
        } catch (Exception e) {
            log.error("Failed to obtain service token: {}", e.getMessage());
            throw new RuntimeException("Failed to obtain service token", e);
        }
    }
}
