CREATE TABLE IF NOT EXISTS subscriptions (
    id                       VARCHAR(26)  PRIMARY KEY,
    business_id              VARCHAR(26)  NOT NULL,
    plan_id                  VARCHAR(26)  NOT NULL REFERENCES plans(id),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING_SETUP',
    razorpay_subscription_id VARCHAR(50),
    current_period_start     TIMESTAMPTZ,
    current_period_end       TIMESTAMPTZ,
    calls_used               INT          NOT NULL DEFAULT 0,
    cancel_at_period_end     BOOLEAN      NOT NULL DEFAULT FALSE,
    cancelled_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ
);

CREATE INDEX idx_subscriptions_business_id ON subscriptions(business_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
