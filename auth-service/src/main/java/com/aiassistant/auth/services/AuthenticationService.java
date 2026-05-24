package com.aiassistant.auth.services;

import com.aiassistant.auth.configuration.SecretsConfiguration;
import com.aiassistant.auth.configuration.ServiceConfiguration;
import com.aiassistant.auth.exceptions.AuthFailedException;
import com.aiassistant.auth.models.dao.BusinessAuthEntity;
import com.aiassistant.auth.models.request.ServiceTokenRequest;
import com.aiassistant.auth.models.request.SigninRequest;
import com.aiassistant.auth.models.response.AuthenticationResponse;
import com.aiassistant.auth.models.response.RefreshTokenResponse;
import com.aiassistant.auth.models.response.ServiceTokenResponse;
import com.aiassistant.auth.repository.BusinessAuthRepository;
import com.aiassistant.auth.security.token.TokenPrincipal;
import com.aiassistant.auth.security.token.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    // MVP role/scopes — every business signs in as its own admin. Multi-user
    // support will read these from a user_roles table when that lands.
    private static final List<String> MVP_ROLES = List.of("ROLE_BUSINESS_ADMIN");
    // Business owners use the dashboard to read/write their own business
    // profile, knowledge, and call activity, so the issued JWT carries the
    // tenant-facing scopes for each of those services. Service-to-service
    // scopes ({service}.internal.*) are deliberately NOT granted here —
    // those are only issued via /api/internal/token to service callers.
    private static final List<String> MVP_SCOPES = List.of(
            "business.read", "business.write",
            "knowledge.read", "knowledge.write",
            "calls.read",
            "summary.read");

    private final SecretsConfiguration secretsConfiguration;
    private final ServiceConfiguration serviceConfiguration;
    private final BusinessAuthRepository businessAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    public AuthenticationResponse signIn(SigninRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        BusinessAuthEntity business = businessAuthRepository.findByEmail(email)
                .orElseThrow(() -> new AuthFailedException("Invalid email or password"));

        if (Boolean.FALSE.equals(business.getIsActive())) {
            throw new AuthFailedException("Business account is inactive");
        }
        if (!passwordEncoder.matches(request.getPassword(), business.getPasswordHash())) {
            throw new AuthFailedException("Invalid email or password");
        }

        Map<String, Object> claims = buildUserClaims(business);

        Duration accessTokenExpiry = secretsConfiguration.getJwt().getAccessTokenExpiration();
        Duration refreshTokenExpiry = secretsConfiguration.getJwt().getRefreshTokenExpiration();

        String accessToken = tokenProvider.issue(business.getEmail(), claims, accessTokenExpiry);
        String refreshToken = tokenProvider.issue(business.getEmail(), claims, refreshTokenExpiry);

        log.info("Business signed in id={} email={}", business.getId(), business.getEmail());

        return AuthenticationResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .tokenType(tokenProvider.tokenType())
                .expiresIn(accessTokenExpiry.toSeconds())
                .message("Login successful")
                .build();
    }

    public RefreshTokenResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validate(refreshToken)) {
            throw new AuthFailedException("Invalid or expired refresh token");
        }

        TokenPrincipal principal = tokenProvider.parse(refreshToken);
        String email = principal.getSubject();

        BusinessAuthEntity business = businessAuthRepository.findByEmail(email)
                .orElseThrow(() -> new AuthFailedException("Business not found"));

        Map<String, Object> claims = buildUserClaims(business);

        Duration accessTokenExpiry = secretsConfiguration.getJwt().getAccessTokenExpiration();
        String newAccessToken = tokenProvider.issue(email, claims, accessTokenExpiry);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(accessTokenExpiry.toSeconds())
                .message("Access token refreshed successfully")
                .build();
    }

    public ServiceTokenResponse generateServiceToken(ServiceTokenRequest request) {
        SecretsConfiguration.ServiceCredentials creds =
                secretsConfiguration.getServices().get(request.getClientId());
        if (creds == null || !creds.getPassword().equals(request.getClientSecret())) {
            throw new AuthFailedException("Invalid clientId or clientSecret");
        }

        ServiceConfiguration.Auth auth = serviceConfiguration.getAuth();
        if (auth == null || auth.getPolicy() == null || auth.getPolicy().getServices() == null) {
            throw new AuthFailedException("Auth policy not configured");
        }

        ServiceConfiguration.PolicyRule rule = auth.getPolicy().getServices().get(request.getAudience());
        if (rule == null || rule.getScopes() == null) {
            throw new AuthFailedException("Service not allowed to access audience: " + request.getAudience());
        }

        List<String> requestedScopes = request.getScopes();
        if (requestedScopes == null || requestedScopes.isEmpty()
                || !rule.getScopes().containsAll(requestedScopes)) {
            throw new AuthFailedException("Requested scopes not allowed. Requested=" + requestedScopes
                    + ", Allowed=" + rule.getScopes());
        }

        Duration expiry = secretsConfiguration.getJwt().getServiceTokenExpiration();

        Map<String, Object> claims = new HashMap<>();
        claims.put("aud", request.getAudience());
        claims.put("scope", requestedScopes);

        String token = tokenProvider.issue(request.getClientId(), claims, expiry);

        return ServiceTokenResponse.builder()
                .token(token)
                .expiresIn(expiry.toSeconds())
                .build();
    }

    private Map<String, Object> buildUserClaims(BusinessAuthEntity business) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", business.getId());
        claims.put("businessId", business.getId());
        claims.put("roles", MVP_ROLES);
        claims.put("scopes", MVP_SCOPES);
        return claims;
    }
}
