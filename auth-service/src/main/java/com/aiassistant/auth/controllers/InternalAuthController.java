package com.aiassistant.auth.controllers;

import com.aiassistant.auth.models.request.ServiceTokenRequest;
import com.aiassistant.auth.models.response.ServiceTokenResponse;
import com.aiassistant.auth.services.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthenticationService authenticationService; // your service to create JWTs

    @PostMapping("/token")
    public ResponseEntity<ServiceTokenResponse> getServiceToken(@RequestBody ServiceTokenRequest request) {
        return ResponseEntity.ok(authenticationService.generateServiceToken(request));
    }
}

