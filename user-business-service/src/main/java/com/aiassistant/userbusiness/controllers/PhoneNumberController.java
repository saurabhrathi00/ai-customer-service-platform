package com.aiassistant.userbusiness.controllers;

import com.aiassistant.userbusiness.models.request.AddPhoneNumberRequest;
import com.aiassistant.userbusiness.models.response.PhoneNumberResponse;
import com.aiassistant.userbusiness.services.PhoneNumberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/{id}/phone-numbers")
@RequiredArgsConstructor
public class PhoneNumberController {

    private final PhoneNumberService phoneNumberService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_business.read') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<List<PhoneNumberResponse>> list(@PathVariable("id") String businessId) {
        return ResponseEntity.ok(phoneNumberService.list(businessId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_business.write') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<PhoneNumberResponse> add(@PathVariable("id") String businessId,
                                                   @Valid @RequestBody AddPhoneNumberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(phoneNumberService.add(businessId, request));
    }

    @DeleteMapping("/{numberId}")
    @PreAuthorize("hasAuthority('SCOPE_business.write') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<Void> delete(@PathVariable("id") String businessId,
                                       @PathVariable("numberId") String numberId) {
        phoneNumberService.delete(businessId, numberId);
        return ResponseEntity.noContent().build();
    }
}
