-- Post-call analytics record. One row per completed call, keyed by ULID.
-- call_log_id is the foreign reference to call-orchestration-service's
-- call_logs.id — kept here as a plain VARCHAR (no DB FK) since the two
-- services own different schemas and we don't want a cross-schema FK that
-- couples deployments.
CREATE TABLE IF NOT EXISTS call_summaries (
    id                     VARCHAR(26)  PRIMARY KEY,
    call_log_id            VARCHAR(26)  NOT NULL UNIQUE,
    business_id            VARCHAR(26)  NOT NULL,
    caller_name            VARCHAR(200),
    customer_phone         VARCHAR(32),
    query_type             VARCHAR(64),
    interest_rating        INTEGER,
    interest_reason        TEXT,
    main_concerns          JSONB,
    callback_needed        BOOLEAN      NOT NULL DEFAULT FALSE,
    callback_reason        TEXT,
    unanswered_questions   JSONB,
    summary_text           TEXT,
    provider               VARCHAR(32),
    input_tokens           INTEGER,
    output_tokens          INTEGER,
    total_tokens           INTEGER,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_call_summaries_business
    ON call_summaries (business_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_call_summaries_callback
    ON call_summaries (business_id, callback_needed);
