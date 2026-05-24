package com.aiassistant.summary.controllers;

import com.aiassistant.summary.models.response.CallSummaryResponse;
import com.aiassistant.summary.security.AuthenticatedPrincipal;
import com.aiassistant.summary.services.SummaryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tenant-facing view of {@code call_summaries}. The dashboard polls
 * {@code GET /api/v1/summaries/{businessId}} after a call ends — the
 * write side of summaries is still asynchronous and triggered by call-orch
 * via the internal endpoint.
 *
 * <p>The path's {@code {businessId}} is checked against the JWT's
 * {@code businessId} claim so business A can never read business B's
 * summaries.</p>
 */
@RestController
@RequestMapping("/api/v1/summaries")
@RequiredArgsConstructor
public class TenantSummaryController {

    private final SummaryQueryService summaryQueryService;

    @GetMapping("/{businessId}")
    @PreAuthorize("hasAuthority('SCOPE_summary.read')")
    public ResponseEntity<List<CallSummaryResponse>> list(
            @PathVariable("businessId") String businessId,
            Authentication authentication) {
        ensureTenantMatches(businessId, authentication);
        return ResponseEntity.ok(summaryQueryService.listByBusiness(businessId));
    }

    /** Cheapest possible tenant guard — no shared helper service yet, so the
     *  check lives inline. Extract to a {@code BusinessAccessGuard} bean if
     *  more endpoints land that need the same logic. */
    private static void ensureTenantMatches(String pathBusinessId, Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedPrincipal user)) {
            throw new AccessDeniedException("Access denied");
        }
        if (!pathBusinessId.equals(user.getBusinessId())) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
