package com.aiassistant.incomingcall.provider;

import lombok.Builder;
import lombok.Value;

/** Vendor-neutral inbound call payload extracted by a TelephonyProvider. */
@Value
@Builder
public class IncomingCallRequest {
    /** Vendor's unique call identifier (Twilio CallSid, Plivo CallUUID, etc.). */
    String callId;
    /** E.164 caller number. */
    String fromNumber;
    /** E.164 dialed number — used to look up the owning business. */
    String toNumber;
    /** Optional vendor-specific call status (ringing/answered/etc.). May be null. */
    String callStatus;
}
