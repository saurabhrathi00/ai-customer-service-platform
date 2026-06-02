package com.aiassistant.userbusiness.controllers;

import com.aiassistant.userbusiness.models.response.BusinessLookupResponse;
import com.aiassistant.userbusiness.models.response.ExistsResponse;
import com.aiassistant.userbusiness.services.BusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/business")
@RequiredArgsConstructor
public class InternalBusinessController {

    private final BusinessService businessService;

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('SCOPE_business.internal.read')")
    public ResponseEntity<BusinessLookupResponse> lookup(@RequestParam("phoneNumber") String phoneNumber) {
        return ResponseEntity.ok(businessService.lookupByPhoneNumber(phoneNumber));
    }

    @GetMapping("/{id}/exists")
    @PreAuthorize("hasAuthority('SCOPE_business.internal.read')")
    public ResponseEntity<ExistsResponse> exists(@PathVariable("id") String id) {
        return ResponseEntity.ok(businessService.exists(id));
    }
}
