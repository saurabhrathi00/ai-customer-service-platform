package com.aiassistant.callorchestration.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhook")
public class TelephonyWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelephonyWebhookController.class);

    @PostMapping("/{provider}/status")
    public ResponseEntity<Void> status(@PathVariable("provider") String provider,
                                       HttpServletRequest request) {
        // TODO: dispatch to provider strategy to parse status callback + trigger post-call persistence
        log.info("Provider status callback provider={} uri={}", provider, request.getRequestURI());
        return ResponseEntity.ok().build();
    }
}
