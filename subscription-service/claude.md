# VoxHelperAI — Subscription & Pricing System

## Project Overview

Build a dynamic subscription/pricing system for VoxHelperAI — an AI voice receptionist platform. Businesses subscribe to monthly plans that include a set number of calls, channels, and features. Pricing must be fully dynamic — admin can change plans, prices, limits anytime from an admin panel.

---

## Tech Context

- **Product**: AI Voice Receptionist (replaces human receptionist for businesses)
- **Telephony**: EnableX (channels + usage-based billing)
- **TTS**: ElevenLabs Flash v2.5 (Creator plan, 121K credits/month shared)
- **STT**: ElevenLabs Scribe v2 Realtime (6.11 hrs/month shared)
- **LLM**: Gemini 2.5 Flash (pay-per-token, negligible cost)
- **Target Market**: Indian SMBs — clinics, salons, law firms, restaurants, hotels
- **Currency**: INR (₹)
- **Payment Gateway**: Razorpay (UPI, cards, netbanking, auto-debit for subscriptions)

---

## Current Pricing Model (Default — Admin Can Change)

### Plans

| Plan | Starter | Growth | Pro |
|---|---|---|---|
| Monthly Price | ₹7,000 | ₹15,000 | ₹30,000 |
| Calls Included | 100 | 250 | 500 |
| Max Call Duration | 3 min | 3 min | 5 min |
| Channels (concurrent) | 1 | 2 | 3 |
| Phone Numbers | 1 | 1 | 2 |
| Post-call Summary | ✅ | ✅ | ✅ |
| Availability | 24/7 | 24/7 | 24/7 |
| Languages | Hindi + English | Hindi + English | Multi-language |
| Extra Call Rate | ₹50/call | ₹45/call | ₹40/call |
| CRM Integration | ❌ | ✅ | ✅ |
| Custom Voice | ❌ | ❌ | ✅ |

### Cost Basis (Internal — NOT shown to client)

| Component | Monthly Cost | Type |
|---|---|---|
| ElevenLabs (TTS + STT) | ₹2,000 | Shared across clients |
| Server Infra | ₹2,500 | Shared across clients |
| EnableX Channel | ₹700/channel | Per client |
| EnableX Number | ~₹500/number | Per client |
| EnableX Usage | 30p/min | Per client, variable |
| Gemini 2.5 Flash | ~₹0.50/call | Per client, variable |

---

## System Architecture

```
┌─────────────────────────────────────────────┐
│                 FRONTEND                     │
│                                              │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐ │
│  │ Pricing  │  │ Checkout │  │ Client    │ │
│  │ Page     │  │ Flow     │  │ Dashboard │ │
│  └──────────┘  └──────────┘  └───────────┘ │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │         Admin Panel                   │   │
│  │  - Plan CRUD                         │   │
│  │  - Pricing Editor                    │   │
│  │  - Subscription Management           │   │
│  │  - Usage Analytics                   │   │
│  └──────────────────────────────────────┘   │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────┐
│                 BACKEND API                   │
│                                               │
│  /api/plans          → CRUD plans (dynamic)   │
│  /api/subscriptions  → Subscribe/cancel/renew │
│  /api/payments       → Razorpay integration   │
│  /api/usage          → Track calls/minutes    │
│  /api/admin          → Admin operations       │
│  /api/webhooks       → Razorpay webhooks      │
└──────────────────┬───────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────┐
│               DATABASE                        │
│                                               │
│  plans, subscriptions, payments,              │
│  usage_logs, clients, invoices                │
└──────────────────────────────────────────────┘
```

---

## Database Schema

### plans
```sql
CREATE TABLE plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,              -- 'Starter', 'Growth', 'Pro'
    slug VARCHAR(50) UNIQUE NOT NULL,       -- 'starter', 'growth', 'pro'
    description TEXT,
    price_monthly INT NOT NULL,             -- in paise (₹7,000 = 700000)
    calls_included INT NOT NULL,            -- 100, 250, 500
    max_call_duration_sec INT NOT NULL,     -- 180 (3 min), 300 (5 min)
    channels INT NOT NULL DEFAULT 1,
    phone_numbers INT NOT NULL DEFAULT 1,
    extra_call_rate INT NOT NULL,           -- in paise per call
    features JSONB DEFAULT '{}',           -- flexible feature flags
    -- Example features JSON:
    -- {
    --   "post_call_summary": true,
    --   "crm_integration": false,
    --   "custom_voice": false,
    --   "languages": ["hindi", "english"],
    --   "availability": "24/7"
    -- }
    is_active BOOLEAN DEFAULT true,
    display_order INT DEFAULT 0,
    is_popular BOOLEAN DEFAULT false,       -- highlight badge on UI
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### clients
```sql
CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name VARCHAR(200) NOT NULL,
    owner_name VARCHAR(100),
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(15) NOT NULL,
    industry VARCHAR(50),                   -- 'clinic', 'salon', 'restaurant', etc.
    gst_number VARCHAR(20),
    address TEXT,
    razorpay_customer_id VARCHAR(50),
    enablex_project_id VARCHAR(50),         -- dedicated EnableX project per client
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### subscriptions
```sql
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id),
    plan_id UUID REFERENCES plans(id),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    -- status: 'active', 'paused', 'cancelled', 'expired', 'payment_failed'
    razorpay_subscription_id VARCHAR(50),
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    calls_used INT DEFAULT 0,
    minutes_used INT DEFAULT 0,
    cancel_at_period_end BOOLEAN DEFAULT false,
    cancelled_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### payments
```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id),
    subscription_id UUID REFERENCES subscriptions(id),
    razorpay_payment_id VARCHAR(50),
    razorpay_order_id VARCHAR(50),
    razorpay_signature VARCHAR(100),
    amount INT NOT NULL,                    -- in paise
    currency VARCHAR(3) DEFAULT 'INR',
    status VARCHAR(20) NOT NULL,            -- 'captured', 'failed', 'refunded'
    payment_method VARCHAR(20),             -- 'upi', 'card', 'netbanking'
    invoice_number VARCHAR(30),
    gst_amount INT DEFAULT 0,              -- 18% GST in paise
    receipt_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### usage_logs
```sql
CREATE TABLE usage_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id),
    subscription_id UUID REFERENCES subscriptions(id),
    call_sid VARCHAR(100),                  -- EnableX call ID
    caller_number VARCHAR(15),
    called_number VARCHAR(15),
    direction VARCHAR(10) DEFAULT 'inbound',
    duration_sec INT NOT NULL,
    tts_chars_used INT DEFAULT 0,
    stt_minutes_used DECIMAL(6,2) DEFAULT 0,
    gemini_tokens_used INT DEFAULT 0,
    summary TEXT,
    recording_url TEXT,
    is_extra_call BOOLEAN DEFAULT false,    -- beyond plan limit
    cost_breakdown JSONB DEFAULT '{}',      -- internal cost tracking
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### invoices
```sql
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id),
    subscription_id UUID REFERENCES subscriptions(id),
    invoice_number VARCHAR(30) UNIQUE NOT NULL,
    billing_period_start TIMESTAMPTZ,
    billing_period_end TIMESTAMPTZ,
    plan_amount INT NOT NULL,               -- base plan in paise
    extra_calls_count INT DEFAULT 0,
    extra_calls_amount INT DEFAULT 0,       -- in paise
    subtotal INT NOT NULL,
    gst_amount INT NOT NULL,                -- 18%
    total INT NOT NULL,
    status VARCHAR(20) DEFAULT 'draft',     -- 'draft', 'sent', 'paid', 'overdue'
    pdf_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## API Endpoints

### Public — Pricing Page

```
GET /api/plans
→ Returns all active plans ordered by display_order
→ Used by pricing page to render dynamically
→ Response: { plans: [ { id, name, slug, price_monthly, calls_included, ... } ] }
```

### Client — Subscription Management

```
POST /api/subscriptions/create
→ Body: { plan_id, client_details }
→ Creates Razorpay subscription + client record
→ Returns: { subscription_id, razorpay_checkout_url }

GET /api/subscriptions/:client_id
→ Returns current subscription, usage stats, days remaining

POST /api/subscriptions/:id/cancel
→ Sets cancel_at_period_end = true

POST /api/subscriptions/:id/change-plan
→ Body: { new_plan_id }
→ Handles upgrade/downgrade proration
```

### Payment — Razorpay Integration

```
POST /api/payments/create-order
→ Creates Razorpay order for first payment

POST /api/payments/verify
→ Verifies Razorpay payment signature
→ Activates subscription on success

POST /api/webhooks/razorpay
→ Handles: payment.captured, payment.failed,
           subscription.charged, subscription.cancelled,
           subscription.pending, subscription.halted
→ CRITICAL: Verify webhook signature before processing
```

### Usage Tracking

```
POST /api/usage/log
→ Called after each call ends
→ Body: { client_id, call_sid, duration_sec, tts_chars, stt_minutes, summary }
→ Increments calls_used on subscription
→ Marks is_extra_call if beyond plan limit

GET /api/usage/:client_id/current
→ Returns: { calls_used, calls_remaining, minutes_used, current_period_end }
```

### Admin — Dynamic Pricing

```
GET    /api/admin/plans              → List all plans (active + inactive)
POST   /api/admin/plans              → Create new plan
PUT    /api/admin/plans/:id          → Update plan (price, limits, features)
DELETE /api/admin/plans/:id          → Soft delete (is_active = false)

GET    /api/admin/subscriptions      → List all subscriptions with filters
GET    /api/admin/clients            → List all clients
GET    /api/admin/analytics          → Revenue, usage, churn metrics
POST   /api/admin/plans/:id/publish  → Activate a draft plan
```

---

## Frontend Pages

### 1. Pricing Page (`/pricing`)

Public-facing page showing all active plans.

**Requirements:**
- Fetch plans from `GET /api/plans` — NO hardcoded pricing
- Display plans as cards in a responsive grid
- Highlight the "popular" plan (is_popular = true)
- Show included features with check/cross icons
- Monthly price prominently displayed
- "Extra calls" rate shown
- CTA button: "Start Free Trial" or "Subscribe Now"
- Toggle for monthly/annual pricing (if applicable later)
- Compare plans section at bottom
- Mobile responsive
- FAQ section at bottom

**Design Notes:**
- Target audience: Indian small business owners (not tech-savvy)
- Language: Clean, simple Hindi-English mix option
- Trust signals: "Cancel anytime", "No hidden charges", "24/7 support"
- Comparison with human receptionist cost (₹20,000) as anchor
- Social proof section

### 2. Checkout Flow (`/checkout/:planSlug`)

**Step 1: Business Details**
- Business name, owner name, email, phone
- Industry dropdown (clinic, salon, restaurant, hotel, etc.)
- GST number (optional)

**Step 2: Plan Confirmation**
- Selected plan summary
- Price + 18% GST breakdown
- Total amount

**Step 3: Payment (Razorpay)**
- Embed Razorpay checkout
- Support: UPI, Cards, Netbanking
- For recurring: Razorpay Subscriptions API
- Auto-debit setup for monthly billing

**Step 4: Success**
- Confirmation screen
- Next steps: "Our team will set up your AI receptionist within 24 hours"
- Download invoice option

### 3. Client Dashboard (`/dashboard`)

**After login, client sees:**
- Current plan name and status
- Usage: "42/100 calls used this month"
- Progress bar for calls used
- Days remaining in billing cycle
- Recent call logs with duration and summary
- "Upgrade Plan" button
- Invoice history
- Cancel/pause subscription option

### 4. Admin Panel (`/admin`)

**Accessible only to VoxHelperAI team:**

**Plans Management:**
- List all plans (table view)
- Create new plan form
- Edit any field: price, calls, features, etc.
- Toggle plan active/inactive
- Set display order (drag and drop)
- Mark plan as "popular"
- Preview how pricing page looks after changes

**Subscription Management:**
- List all active subscriptions
- Filter by plan, status, client
- View client usage details
- Manually pause/cancel subscription
- Override limits if needed

**Analytics Dashboard:**
- Total MRR (Monthly Recurring Revenue)
- Active subscribers count
- Churn rate
- Average revenue per client
- Total calls handled this month
- Usage vs capacity (ElevenLabs limits)
- Revenue trend chart

---

## Razorpay Integration Details

### Setup

```
1. Create Razorpay account (razorpay.com)
2. Get API keys (key_id + key_secret)
3. Enable Subscriptions on dashboard
4. Set webhook URL: https://api.voxhelperai.com/api/webhooks/razorpay
5. Configure webhook events:
   - payment.captured
   - payment.failed
   - subscription.activated
   - subscription.charged
   - subscription.completed
   - subscription.cancelled
   - subscription.pending
   - subscription.halted
```

### Subscription Flow

```
1. Admin creates a "Plan" in Razorpay (mirrors our plans table)
   → razorpay.plans.create({ period: "monthly", interval: 1, item: { name, amount } })

2. Client clicks "Subscribe" on pricing page
   → razorpay.subscriptions.create({ plan_id, total_count: 12 })
   → Returns subscription_id + short_url

3. Client completes payment via Razorpay checkout
   → Razorpay sends webhook: subscription.activated

4. Every month Razorpay auto-charges
   → Webhook: subscription.charged → update payments table
   → Webhook: payment.failed → update status, notify client

5. Client cancels
   → razorpay.subscriptions.cancel(sub_id, { cancel_at_cycle_end: true })
```

### Important Razorpay Notes

- Store `razorpay_plan_id` in plans table when syncing
- Keep plans in sync: when admin changes price, create new Razorpay plan
- Existing subscribers stay on old plan until renewal
- New subscribers get new plan
- GST: Add 18% on top of plan price
- Razorpay fees: ~2% per transaction (factor into margins)

---

## Dynamic Pricing — How It Works

**Key Principle:** Pricing page reads from database, NOT from code.

### When Admin Changes a Plan Price:

```
1. Admin updates price in admin panel
2. API updates plans table
3. If plan has Razorpay plan_id:
   a. Create NEW Razorpay plan with new price
   b. Store new razorpay_plan_id in plans table
   c. Old Razorpay plan stays active for existing subscribers
4. Pricing page automatically shows new price (fetches from API)
5. New subscribers get new price
6. Existing subscribers stay on old price until renewal/upgrade
```

### When Admin Adds a New Plan:

```
1. Admin fills form in admin panel
2. API creates record in plans table with is_active = false (draft)
3. Admin previews pricing page
4. Admin clicks "Publish" → is_active = true
5. Pricing page immediately shows new plan
6. Create corresponding Razorpay plan for payments
```

### When Admin Removes a Plan:

```
1. Admin sets is_active = false
2. Plan disappears from pricing page
3. Existing subscribers on this plan continue until expiry
4. No new subscriptions allowed on this plan
```

---

## Extra Calls / Overage Billing

When a client exceeds their plan's call limit:

```
1. usage_logs tracks each call
2. After each call, check: calls_used > plan.calls_included?
3. If yes: mark is_extra_call = true in usage_log
4. At end of billing cycle:
   a. Count extra calls
   b. Calculate: extra_calls × plan.extra_call_rate
   c. Create Razorpay "addon" charge
   d. Generate invoice with base + extra breakdown
   e. Charge client
```

---

## Environment Variables

```env
# Razorpay
RAZORPAY_KEY_ID=rzp_live_xxxxx
RAZORPAY_KEY_SECRET=xxxxx
RAZORPAY_WEBHOOK_SECRET=xxxxx

# Database
DATABASE_URL=postgresql://user:pass@host:5432/voxhelperai

# EnableX
ENABLEX_APP_ID=xxxxx
ENABLEX_APP_KEY=xxxxx

# ElevenLabs
ELEVENLABS_API_KEY=xxxxx

# Gemini
GEMINI_API_KEY=xxxxx

# App
APP_URL=https://voxhelperai.com
API_URL=https://api.voxhelperai.com
ADMIN_SECRET=xxxxx

# GST
GST_RATE=18
COMPANY_GST_NUMBER=xxxxx
```

---

## Implementation Order

### Phase 1 — MVP (Week 1-2)
1. Database setup (plans, clients, subscriptions, payments tables)
2. Admin panel — Plans CRUD (create/edit/delete plans)
3. Public pricing page (reads from API)
4. Razorpay integration (one-time payment first, subscription later)
5. Basic checkout flow

### Phase 2 — Subscriptions (Week 3-4)
1. Razorpay recurring subscriptions
2. Webhook handling (payment success/failure)
3. Client dashboard (usage tracking, plan status)
4. Invoice generation (with GST)
5. Email notifications (welcome, payment success, payment failed, usage alerts)

### Phase 3 — Analytics & Polish (Week 5-6)
1. Admin analytics dashboard
2. Overage billing (extra calls)
3. Plan upgrade/downgrade flow with proration
4. Usage alerts (80%, 100% of calls used)
5. Annual pricing toggle (if needed)

---

## Key Rules

1. **NEVER hardcode pricing** — always fetch from database via API
2. **Razorpay plan sync** — every plan change creates a new Razorpay plan
3. **Existing subscribers protected** — price changes don't affect current billing cycle
4. **GST always separate** — show price + GST + total clearly
5. **Usage tracking real-time** — update after every call, not end of month
6. **Webhook idempotency** — handle duplicate Razorpay webhooks gracefully
7. **Invoice auto-generation** — at end of each billing cycle
8. **Soft deletes everywhere** — never hard delete plans, clients, or subscriptions
