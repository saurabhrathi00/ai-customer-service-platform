package com.aiassistant.summary.security;

import com.aiassistant.summary.security.token.TokenPrincipal;
import com.aiassistant.summary.security.token.TokenProvider;
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
                    AbstractAuthenticationToken auth = build(parsed);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception e) {
                    log.warn("Failed to build authentication from JWT: {}", e.getMessage());
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }

    private AbstractAuthenticationToken build(TokenPrincipal parsed) {
        Map<String, Object> attrs = parsed.getAttributes();
        String uid = asString(attrs.get("uid"));
        String businessId = asString(attrs.get("businessId"));
        String audience = asString(attrs.get("aud"));
        List<String> roles = asStringList(attrs.get("roles"));
        List<String> scopes = asStringList(attrs.get("scopes"));
        if (scopes.isEmpty()) {
            scopes = asStringList(attrs.get("scope"));
        }

        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
        scopes.forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                parsed.getSubject(), uid, businessId, audience, roles, scopes);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private static String asString(Object v) { return v == null ? null : v.toString(); }

    private static List<String> asStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof Collection<?> c) {
            return c.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (v instanceof String s) return List.of(s);
        return List.of();
    }
}