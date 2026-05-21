package com.aiassistant.knowledge.controllers;

import com.aiassistant.knowledge.models.request.EscalationRuleRequest;
import com.aiassistant.knowledge.models.response.EscalationRuleResponse;
import com.aiassistant.knowledge.services.EscalationService;
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
@RequestMapping("/api/v1/knowledge/{id}/escalations")
@RequiredArgsConstructor
public class EscalationController {

    private final EscalationService escalationService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_knowledge.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<List<EscalationRuleResponse>> list(@PathVariable("id") String id) {
        return ResponseEntity.ok(escalationService.list(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_knowledge.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<EscalationRuleResponse> create(@PathVariable("id") String id,
                                                         @Valid @RequestBody EscalationRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escalationService.create(id, request));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<EscalationRuleResponse> update(@PathVariable("id") String id,
                                                         @PathVariable("ruleId") String ruleId,
                                                         @Valid @RequestBody EscalationRuleRequest request) {
        return ResponseEntity.ok(escalationService.update(id, ruleId, request));
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('SCOPE_knowledge.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<Void> delete(@PathVariable("id") String id,
                                       @PathVariable("ruleId") String ruleId) {
        escalationService.delete(id, ruleId);
        return ResponseEntity.noContent().build();
    }
}
