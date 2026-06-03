-- ============================================================
-- V8: Provider phone numbers inventory
--
-- Moves phone_number + status to a dedicated provider_phone_numbers
-- table. business_phone_numbers becomes a pure link table.
-- ============================================================

-- 1. Create provider phone number inventory
CREATE TABLE IF NOT EXISTS provider_phone_numbers (
    id            VARCHAR(26)  PRIMARY KEY,
    provider_id   VARCHAR(26)  NOT NULL REFERENCES telephony_providers(id),
    phone_number  VARCHAR(32)  NOT NULL UNIQUE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'available',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- status: 'available', 'assigned', 'released'

CREATE INDEX IF NOT EXISTS idx_provider_phone_numbers_provider_id
    ON provider_phone_numbers (provider_id);

CREATE INDEX IF NOT EXISTS idx_provider_phone_numbers_status
    ON provider_phone_numbers (phone_number, status);

-- 2. Migrate existing data from business_phone_numbers → provider_phone_numbers
INSERT INTO provider_phone_numbers (id, provider_id, phone_number, status, created_at)
SELECT
    id,
    provider_id,
    phone_number,
    CASE WHEN is_active THEN 'assigned' ELSE 'released' END,
    created_at
FROM business_phone_numbers;

-- 3. Add FK to provider_phone_numbers on business_phone_numbers
ALTER TABLE business_phone_numbers
    ADD COLUMN provider_phone_number_id VARCHAR(26) REFERENCES provider_phone_numbers(id);

-- Backfill using matching ids (we used the same id above)
UPDATE business_phone_numbers bpn
    SET provider_phone_number_id = bpn.id;

ALTER TABLE business_phone_numbers
    ALTER COLUMN provider_phone_number_id SET NOT NULL;

-- 4. Drop columns that now live in provider_phone_numbers
ALTER TABLE business_phone_numbers
    DROP COLUMN phone_number,
    DROP COLUMN provider_id,
    DROP COLUMN is_active;

-- 5. Clean up V7 index that's no longer relevant
DROP INDEX IF EXISTS idx_business_phone_numbers_provider_id;
