package com.aiassistant.userbusiness.enums;

/**
 * Cadence shape for owner-facing lead reminders.
 *
 * <ul>
 *   <li>{@link #FIXED} — every reminder fires at the same offset from the
 *       previous one ({@code reminder_interval_minutes}). With the default 15
 *       minutes you get pings at +15, +30, +45 ... ten times.</li>
 *   <li>{@link #INCREMENT} — the offset grows linearly by
 *       {@code reminder_interval_minutes}. Reminder #N fires
 *       {@code N * reminder_interval_minutes} after the previous one,
 *       so backs off as the lead ages without the owner reacting.</li>
 * </ul>
 */
public enum ReminderMode {
    FIXED,
    INCREMENT,
}
