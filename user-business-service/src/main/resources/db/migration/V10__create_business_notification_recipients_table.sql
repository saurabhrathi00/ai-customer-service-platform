-- Additional notification recipients beyond the business's primary whatsapp_number.
-- The signup whatsapp number stays on businesses.whatsapp_number (mandatory primary);
-- rows here are extra people the owner wants to keep in the loop. Notification-service
-- sends the same template to the primary AND every active recipient here.
CREATE TABLE IF NOT EXISTS business_notification_recipients (
    id              VARCHAR(26)  PRIMARY KEY,
    business_id     VARCHAR(26)  NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    whatsapp_number VARCHAR(32)  NOT NULL,
    label           VARCHAR(100),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_brn_business_number UNIQUE (business_id, whatsapp_number)
);

CREATE INDEX IF NOT EXISTS idx_brn_business_active
    ON business_notification_recipients (business_id, is_active);
