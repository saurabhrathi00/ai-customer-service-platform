# CLAUDE.md — knowledge-service

## What This Service Does
Collects, validates, and serves business knowledge that the AI uses during a
live call. Replaces the original file-upload-centric design (ARCH §2.4) with a
**structured + free-form + FAQ** model that gives the LLM clean, deterministic
facts.

Primary consumer: `call-orchestration-service` (on WebSocket connect, fetches
the rendered knowledge text for the system prompt).

Knowledge is captured in 4 layers:

1. **Structured profile** — standard fields applicable to every business
   (hours, address, services, payment, policies, languages)
2. **FAQs** — business-specific Q&A pairs
3. **Free-form text** — "anything else a receptionist should know"
4. **Escalation rules** — guard rails telling the AI when to transfer / decline

The service renders these into a single LLM-ready text block on demand.

---

## Tech Stack
- Java 17, Spring Boot 3.5.x
- PostgreSQL via Supabase (owns `knowledge_schema`)
- Flyway for schema migrations
- Lombok, ULID, SLF4J + Logback
- Caffeine (in-memory cache for rendered output)
- RestClient for outbound calls (with service-token auth)
- Port: **8083** (per ARCH §2.4)

File-upload (PDF/DOCX/OCR) is **Phase 2** — not part of MVP.

---

## Strict Conventions — Follow These Exactly

Mirror what user-business-service and auth-service already do:

- ULIDs for primary keys (no UUID, no auto-increment)
- Config split: `configs/service.properties` + `secrets/secrets.properties`
- DTOs separate from entities; entities never returned from controllers
- Lombok: `@Value @Builder @Jacksonized` for response DTOs, `@Data` for requests
- Services: `@RequiredArgsConstructor` + class-level `@Transactional(readOnly=true)`
- Custom exceptions → `GlobalExceptionHandler` → `ApiError`
- JWT validation for tenant-facing endpoints (mirror
  `user-business-service/security/JwtAuthenticationFilter` + `BusinessAccessGuard`)
- Service-token validation for `/api/internal/**` endpoints
- Every DB query includes `WHERE business_id = ?` (ARCH §5.1)

---

## Database Tables This Service Owns

### `knowledge_schema.business_profile`
Standard structured Q&A. One row per business.

```
id                       VARCHAR(26)   PK (ULID)
business_id              VARCHAR(26)   NOT NULL UNIQUE
business_hours           JSONB         -- {mon:{open,close,closed?}, ..., holidays:[dates]}
address                  TEXT
location_notes           TEXT          -- parking, landmark, floor
alt_phone                VARCHAR(32)
contact_email            VARCHAR(200)
website_url              VARCHAR(500)
languages_spoken         TEXT[]        -- ["en","hi","mr"]
services_offered         JSONB         -- [{name, description, price_range, duration}]
payment_methods          TEXT[]        -- ["cash","upi","card","insurance"]
appointment_policy       TEXT
cancellation_policy      TEXT
refund_policy            TEXT
completeness_score       INTEGER       -- 0-100, computed
created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
updated_at               TIMESTAMPTZ
```

### `knowledge_schema.business_faq`
```
id              VARCHAR(26)  PK (ULID)
business_id     VARCHAR(26)  NOT NULL
question        TEXT         NOT NULL
answer          TEXT         NOT NULL
priority        INTEGER      NOT NULL DEFAULT 0
is_active       BOOLEAN      NOT NULL DEFAULT TRUE
created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
updated_at      TIMESTAMPTZ
INDEX (business_id, is_active, priority DESC)
```

Limit: max 50 active FAQs per business (enforced in service layer).

### `knowledge_schema.business_freeform`
```
id              VARCHAR(26)  PK (ULID)
business_id     VARCHAR(26)  NOT NULL UNIQUE
content         TEXT         -- max 10,000 chars (enforced in DTO)
updated_at      TIMESTAMPTZ
```

### `knowledge_schema.business_escalation_rule`
```
id              VARCHAR(26)  PK (ULID)
business_id     VARCHAR(26)  NOT NULL
trigger_phrase  TEXT         NOT NULL
action          VARCHAR(32)  NOT NULL  -- "TRANSFER" | "CALLBACK" | "DECLINE"
action_message  TEXT
is_active       BOOLEAN      NOT NULL DEFAULT TRUE
created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
INDEX (business_id, is_active)
```

---

## Endpoints

### Tenant-facing (JWT required; `SCOPE_knowledge.read` / `SCOPE_knowledge.write` + tenant match)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/knowledge/{businessId}/profile` | Fetch structured profile |
| PUT | `/api/v1/knowledge/{businessId}/profile` | Upsert structured profile (full or partial) |
| GET | `/api/v1/knowledge/{businessId}/faqs` | List FAQs |
| POST | `/api/v1/knowledge/{businessId}/faqs` | Create FAQ |
| PUT | `/api/v1/knowledge/{businessId}/faqs/{faqId}` | Update FAQ |
| DELETE | `/api/v1/knowledge/{businessId}/faqs/{faqId}` | Delete FAQ |
| GET | `/api/v1/knowledge/{businessId}/freeform` | Get free-form context |
| PUT | `/api/v1/knowledge/{businessId}/freeform` | Upsert free-form context |
| GET | `/api/v1/knowledge/{businessId}/escalations` | List escalation rules |
| POST | `/api/v1/knowledge/{businessId}/escalations` | Create rule |
| PUT | `/api/v1/knowledge/{businessId}/escalations/{ruleId}` | Update rule |
| DELETE | `/api/v1/knowledge/{businessId}/escalations/{ruleId}` | Delete rule |
| GET | `/api/v1/knowledge/{businessId}/completeness` | `{score, missing_fields:[...]}` for dashboard nudges |

### Internal (service token required; `SCOPE_knowledge.internal.read`)

| Method | Path | Description |
|---|---|---|
| GET | `/api/internal/knowledge/{businessId}/rendered` | **Primary endpoint.** Returns assembled LLM-ready text block. Called by call-orchestration-service on WebSocket connect. |
| GET | `/api/internal/knowledge/{businessId}/raw` | Raw JSON of all 4 layers — for ai-conversation-service if it needs structured access. |

### Health
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/health` | Standard UP response |

---

## The `KnowledgeRenderer` — Most Important Component

Pure function: structured data → text block. Heavily unit-tested with snapshot
tests. Output goes straight into the LLM system prompt, so format stability
matters.

Output template:

```
=== BUSINESS PROFILE: {business_name} ===

HOURS:
- Monday-Saturday: 10:00 AM - 7:00 PM
- Sunday: Closed
- Holidays: 2026-08-15, 2026-10-02

LOCATION:
{address}. {location_notes}

CONTACT:
- Alt phone: {alt_phone}
- Email: {contact_email}
- Website: {website_url}

LANGUAGES: English, Hindi, Marathi

SERVICES:
- {name}: {price_range}, {duration}
- ...

PAYMENT METHODS: Cash, UPI, Card, Insurance

APPOINTMENT POLICY:
{appointment_policy}

CANCELLATION: {cancellation_policy}
REFUND: {refund_policy}

=== ADDITIONAL CONTEXT ===
{freeform.content}

=== FREQUENTLY ASKED QUESTIONS ===
Q: {faq.question}
A: {faq.answer}
...
(sorted by priority DESC, only is_active=true)

=== ESCALATION RULES (internal, do not read aloud to caller) ===
- If customer mentions "{trigger_phrase}" → {action}. Say: "{action_message}"
```

Omit sections that are empty (don't emit empty headers).

---

## Caching Strategy

Rendered output is read on every call start — high frequency, expensive to
assemble. Cache it.

- Caffeine in-memory cache, key = `businessId`, TTL = 10 minutes
- Invalidate on **any** mutation to profile / FAQ / freeform / escalation for
  that businessId — wire invalidation into the service layer (not controllers)
- Raw JSON endpoint is NOT cached (used rarely, and consumers may want freshest data)

---

## Validation (upload-time, no real-time pressure)

- Phone numbers: E.164 format
- Email: RFC valid
- Hours: `open < close` per day; `closed=true` skips both
- URLs: must be https
- Free-form text: ≤ 10,000 chars
- FAQ question: ≤ 500 chars; answer: ≤ 2,000 chars
- Max 50 active FAQs per business
- Services: `price_min ≤ price_max`; duration > 0
- Escalation action: enum check (`TRANSFER` | `CALLBACK` | `DECLINE`)

Validation failures → 400 with field-level error map so UI can highlight fields.

---

## Completeness Score

Weighted scoring (0-100), surfaced on dashboard:

| Field group | Weight |
|---|---|
| Hours | 15 |
| Address + location | 10 |
| Contact (alt phone or email) | 5 |
| Services (≥1 entry) | 20 |
| Payment methods | 5 |
| Appointment policy | 10 |
| Cancellation policy | 10 |
| Languages | 5 |
| ≥3 FAQs | 10 |
| Free-form text non-empty | 5 |
| ≥1 escalation rule | 5 |

Recomputed on every profile/FAQ/freeform/escalation mutation. Stored on
`business_profile.completeness_score` for cheap reads.

`GET /completeness` also returns `missing_fields: [...]` so the UI can show
specific nudges ("Add a cancellation policy to improve call quality").

---

## Configuration Required

### `configs/service.properties`
```
configs.businessDb.name=postgres
configs.businessDb.schema=knowledge
configs.businessDb.url=jdbc:postgresql://aws-1-ap-southeast-2.pooler.supabase.com:5432/
configs.businessDb.pool.maximumPoolSize=5
configs.businessDb.pool.minimumIdle=1
configs.businessDb.pool.maxLifetimeMs=540000
configs.businessDb.pool.idleTimeoutMs=300000
configs.businessDb.pool.keepaliveTimeMs=240000
configs.businessDb.pool.connectionTestQuery=SELECT 1

configs.authService.baseUrl=http://localhost:8081/auth-service
configs.userBusinessService.baseUrl=http://localhost:8082/user-business-service

configs.cache.rendered.ttlMinutes=10
configs.cache.rendered.maxEntries=10000
```

### `secrets/secrets.properties`
```
secrets.datasource.username=...
secrets.datasource.password=...
secrets.datasource.driverClassName=org.postgresql.Driver

# JWT verification (must match auth-service)
secrets.jwt.secret=...
secrets.jwt.type=Bearer
secrets.jwt.expectedAudience=knowledge-service

# auth-service client creds
secrets.authService.clientId=knowledge-service
secrets.authService.clientSecret=...
```

---

## Communication With Other Services

| Other service | Why | Direction | Auth |
|---|---|---|---|
| `auth-service` | Issue service tokens | this → auth | clientId/clientSecret |
| `user-business-service` | Verify business exists on profile create | this → ubs | service token (`SCOPE_business.internal.read`) |
| `call-orchestration-service` | Fetch rendered knowledge on call start | call-orch → this | service token (`SCOPE_knowledge.internal.read`) |
| `ai-conversation-service` | (Optional) raw structured fetch for tool-use scenarios | ai → this | service token (`SCOPE_knowledge.internal.read`) |

Update auth-service's service-to-service policy (`configs/service.properties`)
to whitelist these caller→scope pairs before going live.

---

## Security

- `/api/v1/**` → JWT required + `BusinessAccessGuard` (path businessId must
  match JWT claim)
- `/api/internal/**` → service token required, `aud` claim must equal
  `knowledge-service` (once aud enforcement is added platform-wide)
- Every DB query includes `business_id = ?`. Cross-tenant access denied.

---

## What Hasn't Been Built Yet
Everything. Folder + this CLAUDE.md only.

### Build Order
1. `pom.xml`, `Dockerfile`, `application.properties`, logback (copy from user-business-service)
2. App class + `configuration/` (Secrets, Service, DataSource, Security, Cache)
3. Flyway migrations for the 4 tables
4. JPA entities, repositories, mappers
5. JWT filter + `BusinessAccessGuard` (copy-adapt from user-business-service)
6. Service-token filter for `/api/internal/**`
7. DTOs (request/response) + validation annotations
8. Service layer: profile, FAQ, freeform, escalation CRUD
9. **`KnowledgeRenderer`** — pure function with snapshot tests
10. Caffeine cache wiring + invalidation hooks
11. `CompletenessScorer` — pure function with unit tests
12. Controllers (tenant `/api/v1/**` + internal `/api/internal/**`)
13. Integration tests:
    - tenant isolation (cross-business access denied)
    - render output snapshot stability
    - cache invalidation correctness
    - completeness scoring

---

## Phase 2 (Not MVP)
- **File upload** (PDF/DOCX/TXT) as a supplement — extracted text appended to
  rendered output under `=== UPLOADED DOCUMENTS ===`. Adds `business_documents`
  table (id, businessId, fileName, fileType, s3Url, extractedText, uploadedAt)
  and reintroduces the S3 + Apache PDFBox dependencies from the original
  ARCH §2.4 design.
- OCR for image-based menus / brochures
- Versioning / change history (audit log of who changed what when)
- Multi-language rendered output (translate FAQs at render time)
- Embeddings + RAG (only if any business's knowledge grows past ~200 KB — not
  expected for SMB target market)

---

## Reference Patterns
- `user-business-service` — JPA + JWT filter + BusinessAccessGuard, DTO style
- `auth-service/CLAUDE.md` — JWT claims contract + service-token contract +
  service-to-service scope policy
- `call-orchestration-service/CLAUDE.md` — primary internal consumer; see how
  it expects to consume the rendered endpoint
- ARCH §2.4 — original file-upload design (superseded by this v2 spec, but
  Phase 2 work will revive parts of it)
- ARCH §5.1 — tenant isolation rules