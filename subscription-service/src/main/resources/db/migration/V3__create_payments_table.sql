-- All monetary columns (amount, gst_amount) are stored in PAISE (₹1 = 100 paise).
CREATE TABLE IF NOT EXISTS payments (
    id                   VARCHAR(26)  PRIMARY KEY,
    business_id          VARCHAR(26)  NOT NULL,
    subscription_id      VARCHAR(26)  REFERENCES subscriptions(id),
    razorpay_payment_id  VARCHAR(50)  UNIQUE,
    razorpay_order_id    VARCHAR(50),
    amount               INT          NOT NULL,
    currency             VARCHAR(3)   NOT NULL DEFAULT 'INR',
    status               VARCHAR(20)  NOT NULL,
    payment_method       VARCHAR(20),
    gst_amount           INT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_business_id ON payments(business_id);
CREATE INDEX idx_payments_subscription_id ON payments(subscription_id);
