package com.aiassistant.userbusiness.enums;

/**
 * Lead lifecycle. NEW is the only non-terminal state — once any of the
 * other three is set the lead is done and reminders stop.
 *
 * <pre>
 *   NEW ──approve──▶ APPROVED       (customer WA fires per lead type)
 *   NEW ──decline──▶ DECLINED       (appointment only; customer WA with reason)
 *   NEW ──ignore───▶ IGNORED        (no customer notification)
 * </pre>
 */
public enum LeadStatus {
    NEW,
    APPROVED,
    DECLINED,
    IGNORED,
}
