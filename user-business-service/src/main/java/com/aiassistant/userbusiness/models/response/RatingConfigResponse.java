package com.aiassistant.userbusiness.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class RatingConfigResponse {
    String businessId;
    List<RatingConfigEntryResponse> entries;
}
