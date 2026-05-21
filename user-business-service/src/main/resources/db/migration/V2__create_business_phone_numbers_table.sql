CREATE TABLE IF NOT EXISTS business_phone_numbers (
    id            VARCHAR(26)  PRIMARY KEY,
    business_id   VARCHAR(26)  NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    twilio_number VARCHAR(32)  NOT NULL UNIQUE,
    label         VARCHAR(100),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_business_phone_numbers_business_id
    ON business_phone_numbers (business_id);
