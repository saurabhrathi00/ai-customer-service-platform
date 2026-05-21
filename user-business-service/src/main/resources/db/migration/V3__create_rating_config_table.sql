CREATE TABLE IF NOT EXISTS rating_config (
    id          VARCHAR(26)  PRIMARY KEY,
    business_id VARCHAR(26)  NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    signal_key  VARCHAR(64)  NOT NULL,
    score_value INTEGER      NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rating_config_business_signal UNIQUE (business_id, signal_key)
);

CREATE INDEX IF NOT EXISTS idx_rating_config_business_id
    ON rating_config (business_id);
