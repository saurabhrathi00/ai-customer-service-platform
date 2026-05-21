package com.aiassistant.aiconversation.security.token;

import com.aiassistant.aiconversation.configuration.SecretsConfiguration;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public final class JwtTokenProvider implements TokenProvider {

    private final SecretKey key;
    private final String tokenType;

    public JwtTokenProvider(SecretsConfiguration secretsConfiguration) {
        SecretsConfiguration.Jwt jwt = secretsConfiguration.getJwt();
        if (jwt == null || jwt.getSecret() == null || jwt.getSecret().isBlank()) {
            throw new IllegalStateException("secrets.jwt.secret is not configured");
        }
        this.key = Keys.hmacShaKeyFor(jwt.getSecret().getBytes(StandardCharsets.UTF_8));
        this.tokenType = jwt.getType() == null ? "Bearer" : jwt.getType();
    }

    @Override public String tokenType() { return tokenType; }

    @Override
    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TokenPrincipal parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Map<String, Object> attrs = new HashMap<>(c);
        attrs.remove(Claims.SUBJECT);
        attrs.remove(Claims.ISSUED_AT);
        attrs.remove(Claims.EXPIRATION);
        return new TokenPrincipal(c.getSubject(), attrs);
    }
}