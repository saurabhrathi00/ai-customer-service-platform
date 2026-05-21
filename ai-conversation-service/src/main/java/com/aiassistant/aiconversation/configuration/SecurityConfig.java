package com.aiassistant.aiconversation.configuration;

import com.aiassistant.aiconversation.models.error.ApiError;
import com.aiassistant.aiconversation.security.JwtAuthenticationFilter;
import com.aiassistant.aiconversation.security.token.TokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenProvider tokenProvider) {
        return new JwtAuthenticationFilter(tokenProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        // WS upgrade auth is enforced by the handshake interceptor.
                        .requestMatchers("/ws/conversation/**").permitAll()
                        .requestMatchers("/api/internal/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthEntryPoint(objectMapper))
                        .accessDeniedHandler(jsonAccessDeniedHandler(objectMapper))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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