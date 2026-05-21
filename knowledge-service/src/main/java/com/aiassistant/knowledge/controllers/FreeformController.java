package com.aiassistant.knowledge.controllers;

import com.aiassistant.knowledge.models.request.FreeformRequest;
import com.aiassistant.knowledge.models.response.FreeformResponse;
import com.aiassistant.knowledge.services.FreeformService;
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
@RequestMapping("/api/v1/knowledge/{id}/freeform")
@RequiredArgsConstructor
public class FreeformController {

    private final FreeformService freeformService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_knowledge.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<FreeformResponse> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(freeformService.get(id));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SCOPE_knowledge.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<FreeformResponse> upsert(@PathVariable("id") String id,
                                                   @Valid @RequestBody FreeformRequest request) {
        return ResponseEntity.ok(freeformService.upsert(id, request));
    }
}
