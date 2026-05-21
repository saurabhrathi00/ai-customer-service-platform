# CLAUDE.md — call-orchestration-service

## What This Service Does
Runs the live phone call. When Twilio (or any telephony provider) opens a
WebSocket Media Stream to this service, it:

1. Accepts the WebSocket from the provider for a given `CallSid`
2. Loads business knowledge from `knowledge-service` (one-time on connect)
3. Opens a **second** WebSocket to `ai-conversation-service` and sends `INIT`
   with the rendered knowledge
4. Streams customer audio → Speech-to-Text → text
5. Forwards each utterance as a `MESSAGE` frame to ai-conversation-service;
   receives `RESPONSE` (or `KNOWLEDGE_REQUEST` / `CALLBACK_NEEDED`)
6. Converts AI reply text → audio (Text-to-Speech) → streams back to the provider
7. Maintains a running transcript in memory for the call's lifetime
8. On call end: sends `END` to ai-conv; receives a final `HISTORY` frame back;
   runs the **single post-call persistence flow** (`PostCallOrchestrator
   .finalizeCall`) — saves `call_logs`, fires async summary, fires async
   callback notification if `callbackRequested`
9. If the WS to ai-conv drops without a clean `END`/`HISTORY`, ai-conv falls
   back to `PUT /api/internal/calls/{conversationId}/history` — same
   `finalizeCall` flow runs; first-wins idempotency guard prevents double work

This is the most stateful service in the platform — one in-memory session per
active call, no persistence during the call.

---

## Tech Stack
- Java 17, Spring Boot 3.5.x
- Spring WebSocket (`spring-boot-starter-websocket`) — server-side
- PostgreSQL via Supabase (owns `calls_schema`, table `call_logs`)
- Flyway for schema migrations
- Lombok, ULID, SLF4J + Logback
- Twilio Java SDK (only inside the Twilio media-stream handler)
- One STT provider SDK (Deepgram or Twilio built-in — chosen at config)
- One TTS provider SDK (ElevenLabs or Polly — chosen at config)
- RestClient for outbound calls to other services (with service-token auth)

---

## Strict Conventions — Follow These Exactly

Mirror what user-business-service and incoming-call-service already do:

- ULIDs for primary keys (no UUID, no auto-increment)
- Config split: `configs/service.properties` + `secrets/secrets.properties`
- DTOs separate from entities; entities never returned from controllers
- Lombok: `@Value @Builder @Jacksonized` for response DTOs, `@Data` for requests
- Services: `@RequiredArgsConstructor` + class-level `@Transactional(readOnly=true)`
- Custom exceptions → `GlobalExceptionHandler` → `ApiError`
- JWT validation for tenant-facing endpoints (mirror
  `user-business-service/security/JwtAuthenticationFilter` + `BusinessAccessGuard`)
- Service-token validation for `/api/internal/**` endpoints
- Provider webhook endpoints (`/api/v1/webhook/**`) → signature-only auth,
  delegated to the provider strategy (same pattern as incoming-call-service)

---

## Provider Strategy Pattern — Required

This service must not hardcode Twilio. Follow the same strategy pattern
incoming-call-service uses, extended for media streams.

### `telephony/TelephonyProvider`
Same interface incoming-call-service defines. Reused for status callbacks here.

### `telephony/TelephonyMediaStreamHandler`
New interface — one impl per provider.

```java
public interface TelephonyMediaStreamHandler {
    String providerId();                                // "twilio", "plivo", ...
    void onConnect(CallSession session, Map<String,String> connectParams);
    void onAudioFrame(CallSession session, byte[] audioPayload, AudioCodec codec);
    void onProviderEvent(CallSession session, String eventType, Map<String,Object> payload);
    void onDisconnect(CallSession session, String reason);
    /** Encode an outbound audio chunk in this provider's expected format. */
    byte[] encodeOutboundAudio(CallSession session, byte[] pcm16k);
}
```

`CallSession` is the provider-agnostic in-memory state (callId, businessId,
customerPhone, transcript, conversation history, knowledge text, started-at).

### `telephony/twilio/TwilioMediaStreamHandler implements …`
- Parses Twilio's Media Stream JSON frames (`event=start|media|mark|stop`,
  base64 mu-law payloads at 8 kHz)
- Decodes mu-law → PCM for the STT pipeline
- Encodes outbound PCM → mu-law and sends `media` JSON frames back

### Registry
`TelephonyMediaStreamHandlerRegistry` auto-discovers all beans by `providerId()`.
A single WebSocket endpoint dispatches to the right handler based on the URL
path (`/ws/{provider}/call/{callId}`) — same approach as the HTTP webhook
controller in incoming-call-service.

### STT and TTS abstractions (same pattern)
- `transcription/SpeechToTextProvider` — single method
  `Stream<TranscriptChunk> transcribe(Flow<AudioChunk> audio)`
- `voice/TextToSpeechProvider` — `byte[] synthesize(String text, VoiceProfile voice)`
- One impl per vendor; the active impl is chosen by `configs.stt.provider` /
  `configs.tts.provider`.

These are NOT the provider strategy from telephony — keep them separate.
Telephony is "how the call arrives"; STT/TTS is "how we hear/speak."

---

## Database Tables This Service Owns

### calls_schema.call_logs
```
id                   VARCHAR(26)  PK (ULID)
business_id          VARCHAR(26)  NOT NULL                -- cross-service ref
customer_phone       VARCHAR(32)
customer_name        VARCHAR(200)
provider             VARCHAR(32)  NOT NULL                -- "twilio", "plivo", …
provider_call_id     VARCHAR(64)  NOT NULL UNIQUE         -- Twilio CallSid, etc.
query_type           VARCHAR(64)
call_summary         TEXT                                  -- filled async post-call
transcript           TEXT                                  -- full conversation
call_duration_secs   INTEGER
feedback_score       INTEGER                               -- 1=yes, 2=no, set by feedback-service
interest_rating      INTEGER
callback_requested   BOOLEAN      NOT NULL DEFAULT FALSE
call_started_at      TIMESTAMPTZ  NOT NULL
call_ended_at        TIMESTAMPTZ
created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
updated_at           TIMESTAMPTZ
```

Every query MUST include `WHERE business_id = ?` — no exceptions.

---

## Endpoints

### Provider-facing (webhook + WebSocket)
| Method | Path | Description |
|---|---|---|
| WS | `/ws/{provider}/call/{callId}` | Provider opens this when call starts. Dispatched to the right `TelephonyMediaStreamHandler`. |
| POST | `/api/v1/webhook/{provider}/status` | Provider status callback (ringing → answered → completed). Signature validated by the provider strategy. |

### Internal (service-to-service, service token required)
| Method | Path | Scope | Description |
|---|---|---|---|
| GET | `/api/internal/calls/{businessId}/recent` | `calls.internal.read` | Recent calls for the business — used by dashboard-service |
| GET | `/api/internal/calls/{businessId}/callbacks` | `calls.internal.read` | Pending callback list |
| PUT | `/api/internal/calls/{callId}/feedback` | `calls.internal.write` | Used by feedback-service after keypress |
| PUT | `/api/internal/calls/{callId}/summary` | `calls.internal.write` | Used by conversation-summary-service after AI summary |
| PUT | `/api/internal/calls/{conversationId}/history` | `calls.internal.write` | **Abrupt-end fallback** from ai-conversation-service. Looks up the active `CallSession` by `conversationId` and runs `PostCallOrchestrator.finalizeCall`. Returns `200 + CallLogResponse` on first finalisation, `204 No Content` if already finalised by the inbound `HISTORY` WS frame. `404` if no active session for that conversationId. |

### Tenant-facing (JWT, requires `SCOPE_calls.read` and tenant match)
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/calls/{businessId}/recent` | Same data as the dashboard endpoint, JWT-protected |

### Health
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/health` | Standard UP response |

---

## In-Memory State

`CallSessionRegistry` (Spring bean) holds `Map<callId, CallSession>`. A session
is created when the WebSocket opens, removed on `finalizeCall` (or after a
timeout guard, e.g. 60 minutes — abandoned-session sweeper).

A session carries:
- `callId`, `conversationId`, `businessId`, `customerPhone`, `provider`, `startedAt`
- Pre-loaded knowledge text (string, fetched once on connect)
- Running transcript (list of `{speaker, text, timestamp}`) — built up during the call
- `history: List<Map<String,String>>` — **populated only at call end** from the
  HISTORY frame (or the REST fallback). The pair `(role, content)` is the
  canonical record we persist.
- `callbackRequested`, `feedbackScore`
- `finalized: AtomicBoolean` — idempotency guard between the HISTORY WS frame
  and the `PUT …/history` REST fallback. First-wins via `compareAndSet`.

Registry supports two lookups:
- `get(callId)` — used during the call by telephony / coordinator
- `findByConversationId(conversationId)` — used by the REST fallback endpoint

No persistence happens during the call. On disconnect, the session is flushed
to `call_logs` in a single transaction inside `PostCallOrchestrator.finalizeCall`.

---

## Configuration Required

### `configs/service.properties`
```
configs.businessDb.name=postgres
configs.businessDb.schema=calls
configs.businessDb.url=jdbc:postgresql://aws-1-ap-southeast-2.pooler.supabase.com:5432/
configs.businessDb.pool.maximumPoolSize=5
configs.businessDb.pool.minimumIdle=1
configs.businessDb.pool.maxLifetimeMs=540000
configs.businessDb.pool.idleTimeoutMs=300000
configs.businessDb.pool.keepaliveTimeMs=240000
configs.businessDb.pool.connectionTestQuery=SELECT 1

configs.authService.baseUrl=http://localhost:8081/auth-service
configs.userBusinessService.baseUrl=http://localhost:8082/user-business-service
configs.knowledgeService.baseUrl=http://localhost:8083/knowledge-service
configs.aiConversationService.baseUrl=http://localhost:8087/ai-conversation-service
configs.conversationSummaryService.baseUrl=http://localhost:8089/conversation-summary-service
configs.notificationService.baseUrl=http://localhost:8090/notification-service

# pick the active STT / TTS providers
configs.stt.provider=deepgram
configs.tts.provider=elevenlabs
```

### `secrets/secrets.properties`
```
secrets.datasource.username=...
secrets.datasource.password=...
secrets.datasource.driverClassName=org.postgresql.Driver

# JWT verification (must match auth-service)
secrets.jwt.secret=...
secrets.jwt.type=Bearer
secrets.jwt.expectedAudience=call-orchestration-service

# auth-service client creds (this service is a client of /api/internal/token)
secrets.authService.clientId=call-orchestration-service
secrets.authService.clientSecret=...

# Twilio (only used by TwilioMediaStreamHandler + provider strategy)
secrets.twilio.account-sid=...
secrets.twilio.auth-token=...

# STT / TTS vendor secrets
secrets.stt.deepgram.apiKey=...
secrets.tts.elevenlabs.apiKey=...
```

---

## Communication With Other Services

| Other service | Why | Direction | Auth |
|---|---|---|---|
| `auth-service` | Issue service tokens | this → auth | clientId/clientSecret |
| `user-business-service` | (Optional) refresh business profile mid-call | this → ubs | service token (`SCOPE_business.internal.read`) |
| `knowledge-service` | Fetch business knowledge text at call start | this → knowledge | service token (`SCOPE_knowledge.internal.read`) |
| `ai-conversation-service` | Get AI reply for each customer utterance + generate summary post-call | this → ai | service token (`SCOPE_ai.internal.invoke`) |
| `conversation-summary-service` | Async trigger to generate + save summary after call ends | this → summary | service token (`SCOPE_summary.internal.invoke`) — fire and forget |
| `notification-service` | Async trigger to email the business if a callback was requested | this → notification | service token (`SCOPE_notify.internal.invoke`) — fire and forget |
| `feedback-service` | Inbound: updates feedback after keypress | feedback → this | service token (`SCOPE_calls.internal.write`) |
| `dashboard-service` | Inbound: reads recent calls / callbacks | dashboard → this | service token (`SCOPE_calls.internal.read`) |

The service-to-service policy (which callers can request which scopes) lives
in auth-service's `configs/service.properties`. Update there when this service
goes live.

---

## Post-call Flow (`PostCallOrchestrator.finalizeCall`)

A single shared method handles end-of-call work. **Both entry points converge
on it** — duplicate invocations are no-ops via the `CallSession.finalized`
`AtomicBoolean`.

### Entry points
1. **Normal end** — `END` frame sent to ai-conv → ai-conv replies with `HISTORY`
   → `ConversationCoordinator.InboundDispatcher.onHistory` →
   `postCallOrchestrator.finalizeCall(session, history)`
2. **Abrupt end** — ai-conv's WS dropped without `END` →
   `PUT /api/internal/calls/{conversationId}/history` →
   `InternalCallController.updateHistory` → `findByConversationId` →
   `postCallOrchestrator.finalizeCall(session, history)`

### What `finalizeCall` does
1. `compareAndSet(false, true)` on `session.finalized` — first call wins, second
   returns `null` immediately (no double work, no double notification).
2. Stamps `session.history` with the incoming list.
3. **Synchronously** persists `call_logs` via `CallLogService.persistOnDisconnect`:
   - JSON-serialises `session.history` into the `transcript` column
   - Computes `call_duration_secs` from `startedAt → endedAt`
   - Carries `callbackRequested`, `feedbackScore`, provider, customer phone, etc.
4. Fires `@Async("postCallExecutor")` → `triggerSummary(callLog)` → POSTs the
   serialised transcript to `conversation-summary-service`.
5. Fires `@Async("postCallExecutor")` → `triggerCallbackNotification(callLog)`
   — no-op unless `callbackRequested == true`; otherwise POSTs to
   `notification-service` with business email + customer phone + brief summary.
6. Removes the session from `CallSessionRegistry`.

The DB write is synchronous *inside* `finalizeCall` so the row exists before
either async task fires; the async tasks then read from `CallLogEntity`, not
from `CallSession`.

### Failure semantics
- Persistence failure → exception propagates (the WS frame triggers a logged
  error; the REST fallback returns 5xx via `GlobalExceptionHandler`). Session
  stays in registry so a retry can attempt again.
- Async task failure → logged at ERROR, swallowed. Never affects the call's
  persistence or the caller's response.
- No message queue yet (Kafka/RabbitMQ is Phase 2). `@Async` + `postCallExecutor`
  thread pool is the MVP transport.

---

## Security

- All `/ws/**` endpoints: signature check via the provider strategy on the
  initial HTTP upgrade. Twilio signs the upgrade request like a normal webhook.
- `/api/v1/webhook/**`: provider signature only (no JWT).
- `/api/v1/calls/**`: JWT required (mirror `BusinessAccessGuard`).
- `/api/internal/**`: service token required (verify `aud` claim matches
  `call-orchestration-service` once aud enforcement is added platform-wide).
- Every DB query includes `business_id = ?`. Cross-tenant access denied.

---

## Port
`8086` per ARCH §8.

---

## Inbound WS Frames From ai-conversation-service

Mirror of `WsMessageType` (kept locally; no shared module). Outbound from
this service: `INIT`, `MESSAGE`, `END`. Inbound to this service:

| Frame | Handler | Effect |
|---|---|---|
| `RESPONSE` | `onResponse` | Appends to in-memory transcript; pushes text to TTS via `CallEventListener.onAiReply` |
| `KNOWLEDGE_REQUEST` | `onKnowledgeRequest` | Refetches knowledge if missing; re-sends `INIT` |
| `CALLBACK_NEEDED` | `onCallbackNeeded` | Sets `session.callbackRequested = true`; pushes callback announcement to TTS + hangup |
| `HISTORY` | `onHistory` | **Normal-end signal.** Stashes `history` on session; invokes `PostCallOrchestrator.finalizeCall`. |
| `ERROR` | `onError` | Logged; non-fatal |

`onClosed` (WS dropped) does **not** itself trigger `finalizeCall` — ai-conv
is responsible for the REST fallback when it can't deliver `HISTORY` on the
socket. This keeps the trigger explicit and avoids double-finalising when the
HISTORY frame arrives just before the socket closes.

---

## Build Status

Core scaffolding and the live-call loop are built. Items still open:
- Interest-score computation from business `rating_config`
- Abandoned-session sweeper (60-minute timeout guard)
- Integration tests covering both finalisation paths (HISTORY frame + REST
  fallback) and the idempotency guard
- `aud` claim enforcement on internal endpoints once the platform-wide policy
  lands

---

## Reference Patterns
- `incoming-call-service` — provider strategy boilerplate to copy
- `user-business-service/security` — JWT filter + tenant guard
- `auth-service/CLAUDE.md` — JWT claims contract + service-token contract
- ARCH §2.7 — service spec, §4 — full call flow, §5.1 — data isolation rules
