CREATE TABLE IF NOT EXISTS plans (
    id                    VARCHAR(26)  PRIMARY KEY,
    name                  VARCHAR(50)  NOT NULL,
    slug                  VARCHAR(50)  NOT NULL UNIQUE,
    description           TEXT,
    price_monthly         INT          NOT NULL,
    calls_included        INT          NOT NULL,
    max_call_duration_sec INT          NOT NULL DEFAULT 180,
    channels              INT          NOT NULL DEFAULT 1,
    phone_numbers         INT          NOT NULL DEFAULT 1,
    extra_call_rate       INT          NOT NULL,
    features              JSONB        NOT NULL DEFAULT '{}',
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order         INT          NOT NULL DEFAULT 0,
    is_popular            BOOLEAN      NOT NULL DEFAULT FALSE,
    razorpay_plan_id      VARCHAR(50),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ
);
