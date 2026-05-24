-- Per-business knobs for the lead handoff workflow. Owner tunes these
-- from the /configurations page. Created lazily on first read so existing
-- businesses keep working without a backfill.
--
-- Defaults match the MVP spec:
--   threshold = 7.0  (any call with interest_rating >= 7.0 fires a HIGH_INTEREST lead)
--   mode      = FIXED reminder every 15 min
--   max       = 10 reminders, then stop
--
-- reminder_mode = INCREMENT means delays grow linearly:
--   reminder #1 at +15m, #2 at +30m, #3 at +45m, ...
-- FIXED means every reminder fires at reminder_interval_minutes from the last:
--   reminder #1 at +15m, #2 at +30m, #3 at +45m, ...
-- (with reminder_interval_minutes=15 they look identical; INCREMENT only
--  diverges if owner picks a different interval.)

CREATE TABLE IF NOT EXISTS lead_notification_settings (
    business_id                 VARCHAR(26)  PRIMARY KEY,
    high_interest_threshold     NUMERIC(3,1) NOT NULL DEFAULT 7.0,
    reminder_mode               VARCHAR(16)  NOT NULL DEFAULT 'FIXED',
    reminder_interval_minutes   INTEGER      NOT NULL DEFAULT 15,
    max_reminders               INTEGER      NOT NULL DEFAULT 10,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ
);
