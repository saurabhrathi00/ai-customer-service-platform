package com.aiassistant.userbusiness.enums;

/** Where a lead decision came from — useful for audit and to render the
 *  "decided via WhatsApp" / "decided via dashboard" line on the lead detail
 *  page after the fact. */
public enum LeadDecisionChannel {
    DASHBOARD,
    WHATSAPP,
}
