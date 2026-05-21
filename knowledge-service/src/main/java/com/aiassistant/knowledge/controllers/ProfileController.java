package com.aiassistant.knowledge.controllers;

import com.aiassistant.knowledge.models.request.UpsertProfileRequest;
import com.aiassistant.knowledge.models.response.CompletenessResponse;
import com.aiassistant.knowledge.models.response.ProfileResponse;
import com.aiassistant.knowledge.services.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/{id}")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<ProfileResponse> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(profileService.get(id));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<ProfileResponse> upsert(@PathVariable("id") String id,
                                                  @Valid @RequestBody UpsertProfileRequest request) {
        return ResponseEntity.ok(profileService.upsert(id, request));
    }

    @GetMapping("/completeness")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<CompletenessResponse> completeness(@PathVariable("id") String id) {
        return ResponseEntity.ok(profileService.completeness(id));
    }
}
