-- =====================================================================
-- Seed: Invogue Building Systems Pvt Ltd
-- Source: https://www.invoguebuildings.com/
--
-- Schemas:
--   business.*   (owned by user-business-service)
--   knowledge.*  (owned by knowledge-service)
--
-- Password for login: Invogue@2026   (bcrypt cost-10 hash below)
-- Replace BUSINESS_ID if you want a different ULID — it is referenced in
-- every subsequent insert.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1) business schema
-- ---------------------------------------------------------------------

INSERT INTO business.businesses (
    id, name, email, password_hash, category, description, location, operating_hours, is_active
) VALUES (
    '01KS6SY1R7F51YWP0PCYWQ3Z83',
    'Invogue Building Systems Pvt Ltd',
    'contact@invoguebuildings.com',
    '$2b$10$xaYIWby0Nz1l0448VpQ7eu/JwaGOYAqpuyQSvHiXhRJY4/YbUaR9m',
    'Pre-Engineered Buildings (PEB) Manufacturer',
    'Established April 2011, Invogue Building Systems specializes in Pre-Engineered Buildings, Roofing & Cladding, Decking Sheet, False Ceiling Systems, Polycarbonate Sheets and Light Gauge Framing Systems. Founded by R.P. Singh with 40+ years of combined industry experience.',
    'Plot No.17, 1st Floor, F.I.E, Patparganj Industrial Estate, Delhi-110092',
    'Mon-Sat 09:30-18:30',
    TRUE
);

INSERT INTO business.business_phone_numbers (id, business_id, twilio_number, label, is_active) VALUES
    ('01KS6SY1R7JBTDN7GGPG79Y3TX', '01KS6SY1R7F51YWP0PCYWQ3Z83', '+918045812623', 'Primary', TRUE);

INSERT INTO business.rating_config (id, business_id, signal_key, score_value) VALUES
    ('01KS6SY1R7HE8WZCZZZ3K9EXSF', '01KS6SY1R7F51YWP0PCYWQ3Z83', 'LONG_CALL',            2),
    ('01KS6SY1R7CQ01QVNZDDATNZ7E', '01KS6SY1R7F51YWP0PCYWQ3Z83', 'POSITIVE_FEEDBACK',    2),
    ('01KS6SY1R703GW05WJ16PFHKCY', '01KS6SY1R7F51YWP0PCYWQ3Z83', 'CALLBACK_REQUESTED',   3),
    ('01KS6SY1R720N2AVYNXXXW6E6F', '01KS6SY1R7F51YWP0PCYWQ3Z83', 'NEGATIVE_FEEDBACK',   -1),
    ('01KS6SY1R7ZQD2VK0J120YXWGS', '01KS6SY1R7F51YWP0PCYWQ3Z83', 'SHORT_CALL',          -2),
    ('01KS6SY1R79KWX26E932ZW9D2B', '01KS6SY1R7F51YWP0PCYWQ3Z83', 'AI_COULD_NOT_ANSWER',  1);

-- ---------------------------------------------------------------------
-- 2) knowledge schema
-- ---------------------------------------------------------------------

INSERT INTO knowledge.business_profile (
    id, business_id, business_hours, address, location_notes, alt_phone, contact_email, website_url,
    languages_spoken, services_offered, payment_methods, appointment_policy, completeness_score
) VALUES (
    '01KS6SY1R72A93AXH3WH942ZF8',
    '01KS6SY1R7F51YWP0PCYWQ3Z83',
    '{"mon":"09:30-18:30","tue":"09:30-18:30","wed":"09:30-18:30","thu":"09:30-18:30","fri":"09:30-18:30","sat":"09:30-18:30","sun":"closed"}'::jsonb,
    'Plot No.17, 1st Floor, F.I.E, Patparganj Industrial Estate, Delhi-110092',
    'Plant 2: Khasra No 180K/180KH/180G/180GH, Yusufpur Isapur, Modinagar-Hapur Road, Ghaziabad, UP 245304. Main Office: Plot 3H & 3I, Gautam Buddha Nagar, Ecotech-2, Udyog Vihar, Greater Noida, UP 201306.',
    '+918045812623',
    'contact@invoguebuildings.com',
    'https://www.invoguebuildings.com/',
    ARRAY['en','hi'],
    '[
      {"name":"Pre-Engineered Metal Building Systems","description":"Design, fabrication and erection of PEB structures for industrial, warehousing and commercial use."},
      {"name":"Roofing and Cladding","description":"Metal roofing and wall cladding solutions for industrial sheds and commercial buildings."},
      {"name":"Decking Sheet","description":"Composite floor decking sheets for multi-storey steel structures."},
      {"name":"False Ceiling Systems","description":"Suspended ceiling systems for commercial interiors."},
      {"name":"Polycarbonate Sheet","description":"Translucent polycarbonate roofing and skylight solutions."},
      {"name":"Light Gauge Framing System (LGFS)","description":"Cold-formed steel framing for lightweight construction."},
      {"name":"Prefabricated Structures","description":"Modular prefab buildings including roof-top structures."}
    ]'::jsonb,
    ARRAY['bank_transfer','cheque','rtgs','neft'],
    'Site visits and quotations are by prior appointment. Domestic (India) inquiries only.',
    70
);

INSERT INTO knowledge.business_faq (id, business_id, question, answer, priority) VALUES
    ('01KS6SY1R78STVG9E6MV0Z1Q1V', '01KS6SY1R7F51YWP0PCYWQ3Z83',
     'What does Invogue Building Systems manufacture?',
     'We manufacture Pre-Engineered Buildings (PEB), roofing and cladding systems, decking sheets, false ceiling systems, polycarbonate sheets, and Light Gauge Framing Systems (LGFS).',
     10),
    ('01KS6SY1R71ZG73SAVW38PZK92', '01KS6SY1R7F51YWP0PCYWQ3Z83',
     'Where are you located?',
     'Our head office is in Patparganj Industrial Estate, Delhi-110092. We have a manufacturing plant in Modinagar-Hapur Road, Ghaziabad (UP), and our main office in Ecotech-2, Greater Noida (UP).',
     9),
    ('01KS6SY1R72WGJN8WFGPCKWJD5', '01KS6SY1R7F51YWP0PCYWQ3Z83',
     'What are your operating hours?',
     'We are open Monday to Saturday, 9:30 AM to 6:30 PM. Closed on Sundays.',
     8),
    ('01KS6SY2A1Q3KCQYG7XZ9N8M2T', '01KS6SY1R7F51YWP0PCYWQ3Z83',
     'Do you accept international orders?',
     'No, we currently accept domestic (India) inquiries only.',
     7),
    ('01KS6SY2A2D4P3VC8W6E1R5J0H', '01KS6SY1R7F51YWP0PCYWQ3Z83',
     'When was the company established and who founded it?',
     'Invogue Building Systems Pvt Ltd was established in April 2011 and was founded by Mr R.P. Singh, who brings over 40 years of combined industry experience.',
     5),
    ('01KS6SY2A3B5N4WD9X7F2T6K1G', '01KS6SY1R7F51YWP0PCYWQ3Z83',
     'How can I request a quotation?',
     'You can request a quotation by calling +91 80458 12623 or emailing contact@invoguebuildings.com with your project details, location, and approximate area/dimensions.',
     9);

INSERT INTO knowledge.business_freeform (id, business_id, content) VALUES (
    '01KS6SY2A4C6M5XE0Y8G3U7L2F',
    '01KS6SY1R7F51YWP0PCYWQ3Z83',
    E'Invogue Building Systems Pvt Ltd is a Delhi-based Pre-Engineered Buildings (PEB) manufacturer established in April 2011 by Mr R.P. Singh.\n\nCore offerings:\n- Pre-Engineered Metal Building Systems for industrial sheds, warehouses, factories and commercial buildings.\n- Roofing and cladding (metal sheets), decking sheets for composite floors, polycarbonate sheets for skylights.\n- False ceiling systems for commercial interiors.\n- Light Gauge Framing System (LGFS) for lightweight cold-formed steel construction.\n- Roof-top structures and modular prefabricated buildings.\n\nManufacturing footprint:\n- Head Office: Patparganj Industrial Estate, Delhi-110092.\n- Plant 2: Yusufpur Isapur, Modinagar-Hapur Road, Ghaziabad, UP 245304.\n- Main Office: Ecotech-2, Udyog Vihar, Greater Noida, UP 201306.\n\nCompany credentials:\n- GST: 09AACCI5934Q1ZW.\n- Trusted Seller verified on TradeIndia.\n- 40+ years of combined leadership experience under founder R.P. Singh.\n\nCustomer policy: domestic (India) inquiries only. Site visits and quotations by appointment.\n\nContact: +91 80458 12623 | contact@invoguebuildings.com | https://www.invoguebuildings.com/'
);

COMMIT;