package com.aiassistant.auth.security.token;

import com.aiassistant.auth.configuration.SecretsConfiguration;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public final class JwtTokenProvider implements TokenProvider {
    private final SecretKey key;
    private final MacAlgorithm alg = Jwts.SIG.HS512;
    private final String tokenType;

    public JwtTokenProvider(SecretsConfiguration secretsConfiguration) {
        // Convert secret string to SecretKey
        this.key = Keys.hmacShaKeyFor(
                secretsConfiguration.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)
        );
        this.tokenType = secretsConfiguration.getJwt().getType(); // "Bearer"
    }

    @Override public String tokenType() {
        return tokenType;
    }

    @Override
    public String issue(String subject, Map<String,Object> attrs, Duration ttl) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl.toMillis());
        return Jwts.builder()
                .claims().add(attrs == null ? Map.of() : attrs).and()
                .subject(subject)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key, alg)
                .compact();
    }

    @Override
    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public TokenPrincipal parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Map<String,Object> attrs = new HashMap<>(c);
        attrs.remove(Claims.SUBJECT);
        attrs.remove(Claims.EXPIRATION);
        attrs.remove(Claims.ISSUED_AT);
        return new TokenPrincipal(c.getSubject(), attrs);
    }
}
