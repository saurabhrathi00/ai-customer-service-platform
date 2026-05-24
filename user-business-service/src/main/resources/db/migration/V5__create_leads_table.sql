-- Leads are the bridge between a finished call and the owner's outbound
-- callback. summary-service POSTs a row here when its post-call LLM detects
-- one of three trigger conditions:
--   APPOINTMENT      caller asked to book / reschedule
--   HIGH_INTEREST    interest rating >= configured threshold (default 7.0)
--   HUMAN_REQUEST    caller asked for a human OR AI couldn't substantively answer
--
-- The owner sees pending leads on the dashboard + via WhatsApp deep-link
-- and chooses one of four terminal actions: approve, decline (appointment-only),
-- do nothing, or call directly. The AI never promises a slot to the caller —
-- the lead is the post-hoc handoff, not a booking.

CREATE TABLE IF NOT EXISTS leads (
    id                      VARCHAR(26)  PRIMARY KEY,
    business_id             VARCHAR(26)  NOT NULL,

    -- Anchor to the source call. One lead per call; re-posting is idempotent.
    call_log_id             VARCHAR(26)  NOT NULL UNIQUE,

    -- Classification.
    lead_type               VARCHAR(32)  NOT NULL,   -- APPOINTMENT | HIGH_INTEREST | HUMAN_REQUEST

    -- Caller info. customer_phone comes from call_logs (caller ID) and is
    -- reliable; the name comes from the transcript and may be null.
    customer_phone          VARCHAR(32),
    customer_name           VARCHAR(200),
    caller_language         VARCHAR(8),

    -- LLM-derived context, shown on dashboard + in WhatsApp template.
    summary                 TEXT,
    interest_rating         NUMERIC(3,1),

    -- APPOINTMENT-only context. NULL for the other lead types.
    service                 VARCHAR(255),
    preferred_window_raw    TEXT,                    -- verbatim from caller
    structured_slots        JSONB,                   -- LLM's parse, list of {date, period}
    suggested_datetime      TIMESTAMPTZ,             -- LLM single best-guess; pre-fill for approve form

    -- State machine. All non-NEW states are terminal — no further reminders.
    status                  VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    confirmed_datetime      TIMESTAMPTZ,
    decline_reason          TEXT,
    decided_at              TIMESTAMPTZ,
    decided_via             VARCHAR(16),             -- DASHBOARD | WHATSAPP

    -- Reminder bookkeeping driven by notification-service's scheduler.
    -- next_reminder_at is the time the scheduler should fire next; when null
    -- (either max reached or lead non-NEW) the scheduler skips this row.
    reminders_sent          INTEGER      NOT NULL DEFAULT 0,
    last_reminder_at        TIMESTAMPTZ,
    next_reminder_at        TIMESTAMPTZ,

    -- Notification fan-out bookkeeping. notification-service polls for rows
    -- where these are null and stamps them after the WhatsApp send succeeds.
    -- owner_notified_at fires once per NEW lead (the initial ping).
    -- customer_notified_at fires once per terminal transition that needs
    -- a customer-facing WhatsApp (APPROVED, or DECLINED for APPOINTMENT
    -- leads; IGNORED never notifies the customer).
    owner_notified_at       TIMESTAMPTZ,
    customer_notified_at    TIMESTAMPTZ,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ
);

-- Dashboard listing.
CREATE INDEX IF NOT EXISTS idx_leads_business_created
    ON leads (business_id, created_at DESC);

-- Reminder scheduler: pending leads whose next reminder is due. Partial
-- index keeps the working set tiny — terminal leads drop out automatically.
CREATE INDEX IF NOT EXISTS idx_leads_reminder_due
    ON leads (next_reminder_at)
    WHERE status = 'NEW' AND next_reminder_at IS NOT NULL;

-- notification-service's owner-fan-out poll. Tiny partial index — rows
-- exit it the moment we stamp owner_notified_at.
CREATE INDEX IF NOT EXISTS idx_leads_owner_unnotified
    ON leads (created_at)
    WHERE owner_notified_at IS NULL;

-- notification-service's customer-fan-out poll. Only terminal rows whose
-- customer WhatsApp hasn't fired yet.
CREATE INDEX IF NOT EXISTS idx_leads_customer_unnotified
    ON leads (decided_at)
    WHERE status IN ('APPROVED', 'DECLINED') AND customer_notified_at IS NULL;
