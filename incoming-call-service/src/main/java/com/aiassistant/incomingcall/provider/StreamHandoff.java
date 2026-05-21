package com.aiassistant.incomingcall.provider;

import lombok.Builder;
import lombok.Value;

/**
 * Data the core hands to a provider so it can build a "open a media stream" response.
 * Core computes the wsUrl from its own config so providers stay free of orchestration knowledge.
 */
@Value
@Builder
public class StreamHandoff {
    String callId;
    String businessId;
    String customerPhone;
    /** Full WebSocket URL the provider should embed in its response. */
    String wsUrl;
}
