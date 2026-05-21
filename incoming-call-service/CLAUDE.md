# CLAUDE.md — incoming-call-service

## What This Service Does
Twilio voice webhook receiver. When a customer dials a business's Twilio
number, Twilio POSTs to this service. This service:

1. Validates the Twilio request signature (X-Twilio-Signature)
2. Looks up which business owns the called Twilio number (via
   user-business-service `/api/internal/business/lookup`)
3. Returns TwiML that tells Twilio to open a Media Stream WebSocket to
   call-orchestration-service for live audio handling

This service is stateless, owns no database, and never holds a call open —
it only answers the initial webhook and hands the call off.

---

## Tech Stack
- Java 17, Spring Boot 3.5.x
- Twilio Java SDK (request validator + TwiML builder)
- Lombok
- WebClient / RestClient for calling user-business-service
- No database

---

## Package Structure (target)
```
com.aiassistant.incomingcall/
├── controllers/
│   └── TwilioWebhookController.java     → /api/v1/twilio/incoming/**
├── services/
│   ├── BusinessLookupService.java       → calls user-business-service
│   └── TwiMLBuilder.java                → builds the Stream TwiML
├── security/
│   ├── TwilioSignatureFilter.java       → validates X-Twilio-Signature
│   └── ServiceTokenClient.java          → fetches/caches auth-service service token
├── clients/
│   └── UserBusinessClient.java          → REST client for /api/internal/business/lookup
├── models/
│   ├── request/                         → Twilio form-encoded payload binders
│   └── response/                        → BusinessLookupResponse (mirror)
├── configuration/
│   ├── SecretsConfiguration.java
│   ├── ServiceConfiguration.java
│   ├── SecurityConfig.java
│   └── HttpClientConfig.java
└── exceptions/
    ├── AppException.java
    ├── TwilioSignatureInvalidException.java
    └── GlobalExceptionHandler.java
```

---

## Conventions
Follow the same rules as user-business-service and auth-service:

- ULIDs (not used here — no DB — but keep dependency available)
- Config split: `configs/service.properties` (non-secret) + `secrets/secrets.properties` (sensitive)
- Request/response DTOs separate from any entities
- Response DTOs: `@Value @Builder @Jacksonized`
- Services: `@RequiredArgsConstructor`, class-level `@Transactional(readOnly=true)` only if there's a DB (here: no)
- Custom exceptions → `GlobalExceptionHandler` → `ApiError`
- Logging: SLF4J, INFO for incoming calls, WARN for signature failures

---

## Endpoints to Build

### Public (Twilio-facing) — `/api/v1/twilio/incoming/`
| Method | Path | Description |
|---|---|---|
| POST | `/call` | Twilio voice webhook on inbound call. Returns TwiML. |
| POST | `/status` | (planned) Twilio status callback (ringing/answered/completed) — proxied to call-orchestration-service |

Twilio sends `application/x-www-form-urlencoded` with at minimum:
`CallSid`, `From`, `To`, `AccountSid`, `CallStatus`.

### Health — `/api/v1/health`
Plain UP/DOWN response.

---

## TwiML Response (success path)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Connect>
    <Stream url="wss://{call-orchestration-host}/ws/call/{CallSid}">
      <Parameter name="businessId" value="{businessId}"/>
      <Parameter name="customerPhone" value="{From}"/>
    </Stream>
  </Connect>
</Response>
```

If the called number does not resolve to an active business, return a
short `<Say>` apology TwiML and hang up — never 5xx to Twilio.

---

## Security

### Twilio signature validation
Every webhook MUST have `X-Twilio-Signature`. Use Twilio's
`RequestValidator`:

```
new RequestValidator(authToken)
    .validate(fullRequestUrl, postParams, signatureHeader)
```

A request that fails validation gets HTTP 403 — no body, no Twilio retry
leakage. The full request URL must be the URL Twilio called (respect
`X-Forwarded-Proto`/`Host` if behind a proxy or the signature won't match).

### Calling user-business-service
The lookup endpoint is `/api/internal/business/lookup` which requires
`SCOPE_business.internal.read`. This service must:

1. Authenticate to auth-service `/api/internal/token` with its
   clientId/clientSecret to receive a service token
2. Cache the token in memory until ~30s before expiry
3. Send `Authorization: Bearer <serviceToken>` on every lookup call

clientId: `incoming-call-service`
audience: `user-business-service`
scope: `business.internal.read`

The auth-service service.properties already has a policy entry permitting
this combination — confirm before first run.

### Own filter chain
- `/api/v1/health` → permitAll
- `/api/v1/twilio/**` → TwilioSignatureFilter only; no JWT required
- everything else → denyAll

---

## Configuration Required

### `configs/service.properties`
```
configs.userBusinessService.baseUrl=http://user-business-service:8082/user-business-service
configs.authService.baseUrl=http://auth-service:8081/auth-service
configs.authService.audience=user-business-service
configs.authService.scopes=business.internal.read
```

### `secrets/secrets.properties`
```
secrets.twilio.account-sid=...
secrets.twilio.auth-token=...           # used by RequestValidator
secrets.authService.clientId=incoming-call-service
secrets.authService.clientSecret=...    # matches auth-service's secrets.services.incoming-call-service.password
```

---

## What Hasn't Been Built Yet
Everything. The folder exists, this CLAUDE.md exists, nothing else. Build
order when we start:

1. `pom.xml`, `Dockerfile`, base properties + logback
2. App class, config classes (Secrets/Service), SecurityConfig (permissive, signature filter only)
3. Twilio signature filter
4. UserBusinessClient + ServiceTokenClient (with caching)
5. `TwilioWebhookController` happy path → TwiML for valid Twilio number
6. Unknown-number fallback TwiML
7. Health endpoint
8. Unit tests for signature validation and TwiML output

---

## Reference Patterns
- `user-business-service/CLAUDE.md` — overall conventions
- `auth-service/CLAUDE.md` — service-token issuance contract (this service is a client of it)
- `user-business-service/src/.../security/JwtAuthenticationFilter.java` — filter style to mirror for `TwilioSignatureFilter`
- ARCH §2.6 — service spec, §6.2 — Twilio request security
