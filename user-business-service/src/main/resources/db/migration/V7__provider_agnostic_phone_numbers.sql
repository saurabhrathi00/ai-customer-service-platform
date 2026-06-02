-- ============================================================
-- V7: Provider-agnostic phone numbers
--
-- 1. Create telephony_providers master table
-- 2. Rename twilio_number → phone_number
-- 3. Add provider_id FK to business_phone_numbers
-- ============================================================

-- 1. Telephony providers master table
CREATE TABLE IF NOT EXISTS telephony_providers (
    id         VARCHAR(26)   PRIMARY KEY,
    name       VARCHAR(50)   NOT NULL,
    slug       VARCHAR(30)   NOT NULL UNIQUE,
    is_active  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Seed the three providers we currently support
INSERT INTO telephony_providers (id, name, slug) VALUES
    ('01PROVIDER0TWILIO000000000', 'Twilio',  'twilio'),
    ('01PROVIDER0EXOTEL000000000', 'Exotel',  'exotel'),
    ('01PROVIDER0ENABLEX00000000', 'EnableX', 'enablex');

-- 2. Rename column
ALTER TABLE business_phone_numbers
    RENAME COLUMN twilio_number TO phone_number;

-- 3. Add provider reference
ALTER TABLE business_phone_numbers
    ADD COLUMN provider_id VARCHAR(26) REFERENCES telephony_providers(id);

-- Backfill existing rows — all current numbers are Exotel
UPDATE business_phone_numbers
    SET provider_id = '01PROVIDER0EXOTEL000000000'
    WHERE provider_id IS NULL;

-- Now make it NOT NULL
ALTER TABLE business_phone_numbers
    ALTER COLUMN provider_id SET NOT NULL;

-- Index for provider lookups
CREATE INDEX IF NOT EXISTS idx_business_phone_numbers_provider_id
    ON business_phone_numbers (provider_id);