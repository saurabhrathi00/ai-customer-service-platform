-- knowledge-service initial schema (Flyway). Runs under schema "knowledge"
-- because spring.flyway.default-schema=knowledge is set.

CREATE TABLE IF NOT EXISTS business_profile (
    id                  VARCHAR(26)  PRIMARY KEY,
    business_id         VARCHAR(26)  NOT NULL UNIQUE,
    business_hours      JSONB,
    address             TEXT,
    location_notes      TEXT,
    alt_phone           VARCHAR(32),
    contact_email       VARCHAR(200),
    website_url         VARCHAR(500),
    languages_spoken    TEXT[],
    services_offered    JSONB,
    payment_methods     TEXT[],
    appointment_policy  TEXT,
    cancellation_policy TEXT,
    refund_policy       TEXT,
    completeness_score  INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS business_faq (
    id          VARCHAR(26)  PRIMARY KEY,
    business_id VARCHAR(26)  NOT NULL,
    question    TEXT         NOT NULL,
    answer      TEXT         NOT NULL,
    priority    INTEGER      NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_business_faq_business
    ON business_faq (business_id, is_active, priority DESC);

CREATE TABLE IF NOT EXISTS business_freeform (
    id          VARCHAR(26)  PRIMARY KEY,
    business_id VARCHAR(26)  NOT NULL UNIQUE,
    content     TEXT,
    updated_at  TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS business_escalation_rule (
    id              VARCHAR(26)  PRIMARY KEY,
    business_id     VARCHAR(26)  NOT NULL,
    trigger_phrase  TEXT         NOT NULL,
    action          VARCHAR(32)  NOT NULL,
    action_message  TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_business_escalation_business
    ON business_escalation_rule (business_id, is_active);
