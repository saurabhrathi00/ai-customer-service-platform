package com.aiassistant.knowledge.controllers;

import com.aiassistant.knowledge.models.response.RawKnowledgeResponse;
import com.aiassistant.knowledge.models.response.RenderedKnowledgeResponse;
import com.aiassistant.knowledge.services.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/knowledge/{id}")
@RequiredArgsConstructor
public class InternalKnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/rendered")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.internal.read')")
    public ResponseEntity<RenderedKnowledgeResponse> rendered(
            @PathVariable("id") String id,
            @RequestParam(value = "businessName", required = false) String businessName) {
        return ResponseEntity.ok(knowledgeService.rendered(id, businessName));
    }

    @GetMapping("/raw")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.internal.read')")
    public ResponseEntity<RawKnowledgeResponse> raw(@PathVariable("id") String id) {
        return ResponseEntity.ok(knowledgeService.raw(id));
    }
}
