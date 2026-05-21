package com.aiassistant.userbusiness.security;

import com.aiassistant.userbusiness.security.token.TokenPrincipal;
import com.aiassistant.userbusiness.security.token.TokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty() && tokenProvider.validate(token)) {
                try {
                    TokenPrincipal parsed = tokenProvider.parse(token);
                    AbstractAuthenticationToken authentication = buildAuthentication(parsed);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    log.warn("Failed to build authentication from JWT: {}", e.getMessage());
                    SecurityContextHolder.clearContext();
                }
            } else {
                log.debug("Bearer token failed validation");
            }
        }
        chain.doFilter(request, response);
    }

    private AbstractAuthenticationToken buildAuthentication(TokenPrincipal parsed) {
        Map<String, Object> attrs = parsed.getAttributes();

        String uid = asString(attrs.get("uid"));
        String businessId = asString(attrs.get("businessId"));
        String audience = asString(attrs.get("aud"));
        List<String> roles = asStringList(attrs.get("roles"));
        List<String> scopes = asStringList(attrs.get("scopes"));
        if (scopes.isEmpty()) {
            // service-token style: single "scope" claim with a list
            scopes = asStringList(attrs.get("scope"));
        }

        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                parsed.getSubject(), uid, businessId, audience, roles, scopes);

        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> col) {
            return col.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (value instanceof String s) {
            return List.of(s);
        }
        return List.of();
    }
}
