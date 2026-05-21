package com.aiassistant.userbusiness.controllers;

import com.aiassistant.userbusiness.models.request.RegisterBusinessRequest;
import com.aiassistant.userbusiness.models.request.UpdateBusinessProfileRequest;
import com.aiassistant.userbusiness.models.response.BusinessResponse;
import com.aiassistant.userbusiness.services.BusinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @PostMapping("/register")
    public ResponseEntity<BusinessResponse> register(@Valid @RequestBody RegisterBusinessRequest request) {
        BusinessResponse response = businessService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('SCOPE_business.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<BusinessResponse> getProfile(@PathVariable("id") String id) {
        return ResponseEntity.ok(businessService.getProfile(id));
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('SCOPE_business.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<BusinessResponse> updateProfile(@PathVariable("id") String id,
                                                          @Valid @RequestBody UpdateBusinessProfileRequest request) {
        return ResponseEntity.ok(businessService.updateProfile(id, request));
    }
}
