package com.aiassistant.userbusiness.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ExistsResponse {
    String id;
    boolean exists;
}
