package com.aiassistant.incomingcall.controllers;

import com.aiassistant.incomingcall.configuration.ServiceConfiguration;
import com.aiassistant.incomingcall.exceptions.BusinessNotFoundException;
import com.aiassistant.incomingcall.models.response.BusinessLookupResponse;
import com.aiassistant.incomingcall.provider.TelephonyProviderRegistry;
import com.aiassistant.incomingcall.services.BusinessLookupService;
import com.aiassistant.incomingcall.provider.IncomingCallRequest;
import com.aiassistant.incomingcall.provider.StreamHandoff;
import com.aiassistant.incomingcall.provider.TelephonyProvider;
import com.aiassistant.incomingcall.provider.TelephonyResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aiassistant.incomingcall.util.PhoneNumberParser;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class TelephonyWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelephonyWebhookController.class);

    private final TelephonyProviderRegistry registry;
    private final BusinessLookupService businessLookupService;
    private final ServiceConfiguration serviceConfiguration;

    @org.springframework.web.bind.annotation.RequestMapping(
            value = "/{provider}/incoming/call",
            method = {org.springframework.web.bind.annotation.RequestMethod.POST,
                      org.springframework.web.bind.annotation.RequestMethod.GET})
    public ResponseEntity<String> incomingCall(@PathVariable("provider") String providerName,
                                               HttpServletRequest request) {
        TelephonyProvider provider = registry.find(providerName)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Unknown provider: " + providerName));

        IncomingCallRequest call = provider.parseRequest(request);

        // Exotel (and others) may send a GET with no body to validate the URL.
        if (call.getCallId() == null && call.getToNumber() == null) {
            log.info("URL validation probe from provider={} (no call data) — returning 200", provider.name());
            return ResponseEntity.ok().body("{\"status\":\"ok\"}");
        }

        log.info("Incoming call via provider={} callId={} From={} To={} CallStatus={}",
                provider.name(), call.getCallId(), call.getFromNumber(),
                call.getToNumber(), call.getCallStatus());

        TelephonyResponse response;
        StreamHandoff handoff = null;
        if (call.getToNumber() == null || call.getToNumber().isBlank()) {
            log.info("No 'to' number — returning fallback (provider={})", provider.name());
            response = provider.buildUnknownNumberResponse();
            return ResponseEntity.ok()
                    .contentType(response.getContentType())
                    .body(response.getBody());
        }

        String normalizedTo = PhoneNumberParser.normalize(call.getToNumber());
        log.info("Normalized To number: raw={} normalized={}", call.getToNumber(), normalizedTo);

        try {
            BusinessLookupResponse business = businessLookupService.lookupByPhoneNumber(normalizedTo);
            if (business == null) {
                log.warn("Business lookup returned null for To={} (provider={}), returning fallback",
                        call.getToNumber(), provider.name());
                response = provider.buildUnknownNumberResponse();
            } else if (Boolean.FALSE.equals(business.getIsActive())) {
                log.warn("Business inactive for To={} (businessId={}, name={}), returning fallback",
                        call.getToNumber(), business.getBusinessId(), business.getName());
                response = provider.buildUnknownNumberResponse();
            } else {
                log.info("Business resolved for To={}: businessId={}, name={}, isActive={}",
                        call.getToNumber(), business.getBusinessId(),
                        business.getName(), business.getIsActive());

                String status = call.getCallStatus();
                boolean isInitialEvent = status == null
                        || status.equalsIgnoreCase("incomingcall")
                        || status.equalsIgnoreCase("ringing");

                if (isInitialEvent) {
                    handoff = StreamHandoff.builder()
                            .callId(call.getCallId())
                            .businessId(business.getBusinessId())
                            .businessName(business.getName())
                            .customerPhone(call.getFromNumber())
                            .wsUrl(buildWsUrl(provider.name(), call.getCallId()))
                            .build();
                    response = provider.buildStreamHandoff(handoff);
                } else {
                    log.info("Skipping handoff for status={} (provider={})", status, provider.name());
                    response = new TelephonyResponse("{\"status\":\"ok\"}", org.springframework.http.MediaType.APPLICATION_JSON);
                }
            }
        } catch (BusinessNotFoundException ex) {
            log.warn("No business mapped to To={} (provider={}), returning fallback",
                    call.getToNumber(), provider.name());
            response = provider.buildUnknownNumberResponse();
        }

        if (handoff != null) {
            provider.afterHandoff(handoff);
        }

        return ResponseEntity.ok()
                .contentType(response.getContentType())
                .body(response.getBody());
    }

    private String buildWsUrl(String providerName, String callId) {
        return serviceConfiguration.getCallOrchestration().getWsBaseUrl()
                + "/ws/" + providerName + "/call/" + callId;
    }
}
