-- Seed EnableX phone number into provider inventory so businesses can assign it.
-- The number must exist here before the UI "Add number" flow will accept it.
INSERT INTO provider_phone_numbers (id, provider_id, phone_number, status, created_at)
VALUES ('01JXHK0000ENABLEX917713', '01PROVIDER0ENABLEX00000000', '917713128713', 'available', NOW())
ON CONFLICT (phone_number) DO UPDATE SET provider_id = '01PROVIDER0ENABLEX00000000';
