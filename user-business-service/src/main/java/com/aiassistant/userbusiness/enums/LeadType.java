package com.aiassistant.userbusiness.enums;

/** Why a lead was raised. Drives which buttons render on the dashboard
 *  detail page and which customer-facing WhatsApp template fires on
 *  approve / decline. */
public enum LeadType {
    /** Caller wanted to book / reschedule an appointment. Owner can
     *  approve with a time (customer gets confirmation WA) or decline
     *  with a reason (customer gets apology WA). */
    APPOINTMENT,
    /** Interest rating crossed the configured threshold but caller didn't
     *  explicitly ask for an appointment / human. Owner approves to
     *  promise a callback; no decline path. */
    HIGH_INTEREST,
    /** Caller asked for a human OR AI couldn't substantively answer a
     *  meaningful question. Same UX as HIGH_INTEREST. */
    HUMAN_REQUEST,
}
