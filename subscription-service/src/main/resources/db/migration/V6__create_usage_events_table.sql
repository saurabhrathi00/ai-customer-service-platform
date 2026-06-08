CREATE TABLE IF NOT EXISTS usage_events (
    id                  VARCHAR(26)  PRIMARY KEY,
    business_id         VARCHAR(26)  NOT NULL,
    subscription_id     VARCHAR(26)  REFERENCES subscriptions(id),
    call_id             VARCHAR(26)  NOT NULL UNIQUE,
    call_duration_secs  INT          NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usage_events_business_id ON usage_events(business_id);
CREATE INDEX idx_usage_events_subscription_id ON usage_events(subscription_id);
