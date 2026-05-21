package com.aiassistant.knowledge.models.response;

import com.aiassistant.knowledge.models.domain.BusinessHours;
import com.aiassistant.knowledge.models.domain.Service;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

@Value
@Builder
@Jacksonized
public class ProfileResponse {
    String id;
    String businessId;
    BusinessHours businessHours;
    String address;
    String locationNotes;
    String altPhone;
    String contactEmail;
    String websiteUrl;
    List<String> languagesSpoken;
    List<Service> servicesOffered;
    List<String> paymentMethods;
    String appointmentPolicy;
    String cancellationPolicy;
    String refundPolicy;
    Integer completenessScore;
    Instant createdAt;
    Instant updatedAt;
}
