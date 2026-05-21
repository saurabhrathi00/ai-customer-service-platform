package com.aiassistant.incomingcall.provider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Per-provider boundary. One implementation per telephony vendor (Twilio, Plivo, Exotel, ...).
 *
 * Lifecycle of a single inbound call:
 *   1. {@link #verifySignature(HttpServletRequest)} — invoked by the generic signature filter
 *      before any business logic. Must throw {@link TelephonySignatureInvalidException} on failure.
 *   2. {@link #parseRequest(HttpServletRequest)} — extract the vendor-neutral call fields.
 *   3. {@link #buildStreamHandoff(StreamHandoff)} — produce the response that tells the vendor
 *      to open a media stream to call-orchestration-service.
 *   4. {@link #buildUnknownNumberResponse()} — fallback response when no business is mapped.
 */
public interface TelephonyProvider {

    /** Lowercase identifier used in the URL path: /api/v1/webhook/{name}/incoming/call. */
    String name();

    void verifySignature(HttpServletRequest request) throws TelephonySignatureInvalidException;

    IncomingCallRequest parseRequest(HttpServletRequest request);

    TelephonyResponse buildStreamHandoff(StreamHandoff handoff);

    TelephonyResponse buildUnknownNumberResponse();
}
