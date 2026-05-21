package com.aiassistant.knowledge.controllers;

import com.aiassistant.knowledge.models.request.FaqRequest;
import com.aiassistant.knowledge.models.response.FaqResponse;
import com.aiassistant.knowledge.services.FaqService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge/{id}/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_knowledge.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<List<FaqResponse>> list(@PathVariable("id") String id) {
        return ResponseEntity.ok(faqService.list(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_knowledge.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<FaqResponse> create(@PathVariable("id") String id,
                                              @Valid @RequestBody FaqRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(faqService.create(id, request));
    }

    @PutMapping("/{faqId}")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<FaqResponse> update(@PathVariable("id") String id,
                                              @PathVariable("faqId") String faqId,
                                              @Valid @RequestBody FaqRequest request) {
        return ResponseEntity.ok(faqService.update(id, faqId, request));
    }

    @DeleteMapping("/{faqId}")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<Void> delete(@PathVariable("id") String id,
                                       @PathVariable("faqId") String faqId) {
        faqService.delete(id, faqId);
        return ResponseEntity.noContent().build();
    }
}
