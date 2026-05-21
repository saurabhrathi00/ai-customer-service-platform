CREATE TABLE IF NOT EXISTS call_logs (
    id                   VARCHAR(26)  PRIMARY KEY,
    business_id          VARCHAR(26)  NOT NULL,
    customer_phone       VARCHAR(32),
    customer_name        VARCHAR(200),
    provider             VARCHAR(32)  NOT NULL,
    provider_call_id     VARCHAR(64)  NOT NULL UNIQUE,
    query_type           VARCHAR(64),
    call_summary         TEXT,
    transcript           TEXT,
    call_duration_secs   INTEGER,
    feedback_score       INTEGER,
    interest_rating      INTEGER,
    callback_requested   BOOLEAN      NOT NULL DEFAULT FALSE,
    call_started_at      TIMESTAMPTZ  NOT NULL,
    call_ended_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_call_logs_business_started
    ON call_logs (business_id, call_started_at DESC);

CREATE INDEX IF NOT EXISTS idx_call_logs_business_callback
    ON call_logs (business_id, callback_requested);