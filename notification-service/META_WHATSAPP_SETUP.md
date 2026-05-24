# Meta WhatsApp Cloud API setup

notification-service ships in **stub mode** by default (`configs.whatsapp.stubMode=true`).
Every "send" is logged but no HTTP call is made. That lets you exercise the full
lead → reminder → terminal pipeline without owning a WhatsApp Business Account.

To go live, do these one-time steps (~30 minutes of clicks + 1–2 days waiting on
Meta to approve templates), then flip `stubMode=false`.

## 1. Meta Business + WhatsApp Business Account

1. Create a [Meta Business Manager](https://business.facebook.com) account if you don't have one.
2. Inside it, create a **WhatsApp Business Account (WABA)**.
3. Add a phone number to the WABA. Two options:
   - **Test number** — Meta gives you a sandbox number you can use immediately.
     Free, limited to 5 verified recipients. Good for dev.
   - **Production number** — verify a number you own (cannot be already on
     WhatsApp). Goes through SMS/voice verification. No recipient limit.
4. Note the **Phone Number ID** (long numeric, visible in the WhatsApp Manager
   → API setup page). This goes into `configs.whatsapp.phoneNumberId`.

## 2. System User access token

Permanent tokens require a system user:

1. Business Manager → Settings → **System Users** → Add → assign role.
2. Generate a token: select your WhatsApp Business Account, give it
   `whatsapp_business_messaging` + `whatsapp_business_management` scopes.
3. Pick **Never expire** for the token. Save it once — Meta won't show it again.
4. Put it in `secrets.whatsapp.accessToken`.

## 3. Register the four message templates

notification-service uses **four** templates. Submit them through
WhatsApp Manager → **Message Templates** → Create. Meta usually approves in
1–2 working days; submit all four together.

Template names below match the defaults in `configs/service.properties`. Pick
language **English** (`en`) unless you override `configs.whatsapp.templateLanguage`.

> **About placeholders:** Meta numbers them `{{1}}`, `{{2}}`, … positionally.
> The body parameters notification-service sends correspond to the bullet list
> under each template in the same order.

---

### 3.1 `owner_new_lead` (Category: Utility)

Lands in the owner's WhatsApp when a new lead is captured AND when a reminder fires.

**Body:**
```
You have a new {{3}} from {{1}} ({{2}}).

{{4}}

Tap the button to review and respond.
```

**Body parameters in order:**
1. Customer name (or `"Unknown"`)
2. Customer phone (or `"no phone"`)
3. Lead type label — one of `"appointment request"`, `"high-interest lead"`, `"needs a human"`
4. AI summary (≤ 3 sentences)

**Button:** Add **one** dynamic URL button.
- Static base = your dashboard URL ending in `/leads/`
  (e.g. `https://app.example.com/leads/`)
- Variable = filled per send with the lead ID, so the link resolves to
  `https://app.example.com/leads/<leadId>`.

---

### 3.2 `customer_appt_confirmed` (Category: Utility)

Sent to the customer the moment the owner confirms an APPOINTMENT lead.

**Body:**
```
Your appointment with {{1}} is confirmed for {{2}}.

We'll see you then. Reply to this message if you need to reschedule.
```

**Body parameters in order:**
1. Business name
2. Confirmed date + time, e.g. `"Wed, May 28 at 10:00 AM"` (notification-service
   formats the slot using IST by default — adjust `SLOT_FORMAT` in
   `NotificationDispatcher.java` if you need a different timezone).

No buttons.

---

### 3.3 `customer_appt_declined` (Category: Utility)

Sent to the customer when the owner declines an APPOINTMENT lead.

**Body:**
```
Sorry — {{1}} couldn't accommodate your appointment request.

Reason: {{2}}

Please call us back so we can find another time.
```

**Body parameters in order:**
1. Business name
2. Decline reason (typed by the owner in the dashboard)

No buttons.

---

### 3.4 `customer_agent_will_connect` (Category: Utility)

Sent to the customer when the owner approves a HIGH_INTEREST or HUMAN_REQUEST
lead — i.e. promises a human callback.

**Body:**
```
Thanks for reaching out to {{1}}.

Our team will connect with you shortly.
```

**Body parameter:**
1. Business name

No buttons.

---

## 4. Wire it up

In `notification-service/configs/service.properties` (or via env vars in
`docker-compose.local.yml`):

```properties
configs.whatsapp.stubMode=false
configs.whatsapp.phoneNumberId=123456789012345
configs.whatsapp.ownerNewLeadTemplate=owner_new_lead
configs.whatsapp.customerApptConfirmedTemplate=customer_appt_confirmed
configs.whatsapp.customerApptDeclinedTemplate=customer_appt_declined
configs.whatsapp.customerAgentWillConnectTemplate=customer_agent_will_connect
configs.whatsapp.templateLanguage=en
configs.dashboard.leadLinkTemplate=https://app.your-domain.com/leads/
```

In `notification-service/secrets/secrets.properties`:

```properties
secrets.whatsapp.accessToken=<your-system-user-token>
```

## 5. Cost (India tier, as of writing)

| Conversation category | Cost per conversation |
|---|---|
| Utility (all our templates) | ~₹0.115 |
| Service (within 24 h of customer-initiated message) | Free (first 1000/mo) |

A "conversation" is a 24-hour window per recipient, not per message. The 4
notifications we send for a single lead all bill as **one** utility
conversation, so the practical unit cost is ~₹0.12 per lead.

## 6. Verify it works

1. Bring up the stack with stubMode=false.
2. Make a test call that triggers a lead (e.g. ask for an appointment).
3. Check notification-service logs for `[wa] sent template=owner_new_lead to=…`.
4. The configured WhatsApp number should receive the message within seconds.

If you see `[wa-stub] template=…` instead, stubMode is still true — double-check
the config got loaded (most common cause: wrong path in the docker-compose volume mount).
