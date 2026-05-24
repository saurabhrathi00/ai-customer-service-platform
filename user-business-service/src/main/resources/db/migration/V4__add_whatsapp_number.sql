-- WhatsApp number used to notify the owner about new appointment requests.
-- E.164, optional at registration time but required before any WhatsApp
-- notification can fire. Single number per business in MVP; multi-recipient
-- support comes in Phase 2 via a separate notification_recipients table.
ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS whatsapp_number VARCHAR(32);
