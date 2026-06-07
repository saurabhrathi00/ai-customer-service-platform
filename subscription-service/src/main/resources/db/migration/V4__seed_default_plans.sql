-- All monetary values are in PAISE (₹1 = 100 paise), matching Razorpay's convention.
-- e.g. price_monthly 700000 = ₹7,000, extra_call_rate 5000 = ₹50/call.
INSERT INTO plans (id, name, slug, description, price_monthly, calls_included, max_call_duration_sec, channels, phone_numbers, extra_call_rate, features, is_active, display_order, is_popular)
VALUES
    ('01PLAN0STARTER000000000000', 'Starter', 'starter',
     'Perfect for small businesses getting started with AI reception.',
     700000, 100, 180, 1, 1, 5000,
     '{"post_call_summary": true, "crm_integration": false, "custom_voice": false, "languages": ["hindi", "english"], "availability": "24/7"}',
     TRUE, 1, FALSE),

    ('01PLAN0GROWTH0000000000000', 'Growth', 'growth',
     'For growing businesses that need more capacity and features.',
     1500000, 250, 180, 2, 1, 4500,
     '{"post_call_summary": true, "crm_integration": true, "custom_voice": false, "languages": ["hindi", "english"], "availability": "24/7"}',
     TRUE, 2, TRUE),

    ('01PLAN0PRO0000000000000000', 'Pro', 'pro',
     'Full-featured plan for high-volume businesses.',
     3000000, 500, 300, 3, 2, 4000,
     '{"post_call_summary": true, "crm_integration": true, "custom_voice": true, "languages": ["hindi", "english", "multi"], "availability": "24/7"}',
     TRUE, 3, FALSE);
