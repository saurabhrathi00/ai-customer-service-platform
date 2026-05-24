package com.aiassistant.notification.configuration;

import com.aiassistant.notification.models.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * notification-service exposes no tenant or internal API in the MVP — it's
 * a poller-and-dispatcher. Health is open; everything else is denied.
 * If we add a debug "trigger now" or webhook in the future, plug it in here.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthEntryPoint(objectMapper))
                        .accessDeniedHandler(jsonAccessDeniedHandler(objectMapper))
                );
        return http.build();
    }

    private AuthenticationEntryPoint jsonAuthEntryPoint(ObjectMapper mapper) {
        return (req, res, ex) -> writeError(res, mapper, HttpStatus.UNAUTHORIZED, ex);
    }

    private AccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper mapper) {
        return (req, res, ex) -> writeError(res, mapper, HttpStatus.FORBIDDEN, ex);
    }

    private void writeError(HttpServletResponse response, ObjectMapper mapper,
                            HttpStatus status, Exception ex) throws java.io.IOException {
        String message = status == HttpStatus.UNAUTHORIZED ? "Authentication required" : "Access denied";
        if ((ex instanceof AuthenticationException || ex instanceof AccessDeniedException)
                && ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message = ex.getMessage();
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(status, message));
    }
}
