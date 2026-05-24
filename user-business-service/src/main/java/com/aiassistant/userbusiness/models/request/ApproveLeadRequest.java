package com.aiassistant.userbusiness.models.request;

import lombok.Data;

import java.time.Instant;

/**
 * Owner-side approve payload.
 *
 * <p>For APPOINTMENT leads {@code confirmedDatetime} is required — it's the
 * exact slot the owner is committing to and what gets sent to the customer
 * in the confirmation WhatsApp. For HIGH_INTEREST / HUMAN_REQUEST leads the
 * field is ignored.</p>
 */
@Data
public class ApproveLeadRequest {
    private Instant confirmedDatetime;
}
