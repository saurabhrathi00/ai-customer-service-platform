# CLAUDE.md — call-orchestration-service

## What This Service Does
Runs the live phone call. When Twilio (or any telephony provider) opens a
WebSocket Media Stream to this service, it:

1. Accepts the WebSocket from the provider for a given `CallSid`
2. Loads business knowledge from `knowledge-service` (one-time REST call on connect)
3. Opens a persistent WebSocket connection to `ai-conversation-service` for this call
4. Sends an `INIT` message to `ai-conversation-service` over that WS with conversationId,
   businessId, and business knowledge
5. Streams customer audio → ElevenLabs STT (WebSocket) → committed transcript text
6. Forwards each committed transcript to `ai-conversation-service` as a `MESSAGE` over WS
7. Receives AI text responses back over the same WS from `ai-conversation-service`
8. Converts AI text → ElevenLabs TTS → audio → streams back to the provider
9. Handles inbound `KNOWLEDGE_REQUEST` from `ai-conversation-service` by resending
   an `INIT` message with the business knowledge
10. Handles inbound `CALLBACK_NEEDED` — marks session flag, AI informs customer
    "Our team will reach you within 24 hours"
11. Maintains the full transcript in memory for the call's lifetime
12. On call end: sends `END` to `ai-conversation-service`, saves `call_logs` row,
    fires async tasks for summary generation, callback notification, and interest
    score computation from business `rating_config`

**Conversation history is NOT maintained here.**
`ai-conversation-service` owns the history — this service only stores the
knowledge text (for re-sending on KNOWLEDGE_REQUEST) and the running transcript
(for saving to DB at call end).

This is the most stateful service in the platform — one in-memory session per
active call, no persistence during the call.

---

## Tech Stack
- Java 17, Spring Boot 3.5.x
- Spring WebSocket (`spring-boot-starter-websocket`) — server-side (accepts Twilio WS)
- Java WebSocket Client (`javax.websocket-client-api`) — client-side (connects to ai-conversation-service)
- PostgreSQL via Supabase (owns `calls_schema`, table `call_logs`)
- Flyway for schema migrations
- Lombok, ULID, SLF4J + Logback
- Twilio Java SDK (only inside the Twilio media-stream handler)
- ElevenLabs STT — primary STT provider (WebSocket, `scribe_v2_realtime` model)
- ElevenLabs TTS — primary TTS provider (WebSocket, streaming)
- RestClient for one-time outbound REST calls (knowledge-service, notification-service, summary-service)

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

## AI Conversation WebSocket Protocol

This service opens one persistent WS connection to `ai-conversation-service` per active call.
All communication uses JSON messages with a `type` field.

### Message Types
```java
public enum WsMessageType {
    // Outbound (call-orchestration → ai-conversation-service)
    INIT,               // First message — send knowledge + conversationId + businessId
    MESSAGE,            // Customer committed transcript — process and respond
    END,                // Call ended — ai-conversation-service clears session

    // Inbound (ai-conversation-service → call-orchestration)
    RESPONSE,           // AI reply text — convert to TTS and send to customer
    KNOWLEDGE_REQUEST,  // ai-conversation-service lost knowledge — resend INIT
    CALLBACK_NEEDED     // AI cannot answer — trigger callback flow
}
```

### Outbound: INIT
Sent immediately after WS connection is established. Also sent in response to KNOWLEDGE_REQUEST.
```json
{
  "type": "INIT",
  "conversationId": "conv_01ABC",
  "businessId": "biz_01XYZ",
  "knowledge": "Business name: Joe's Pizza\nTimings: 9am-9pm\nMenu: ..."
}
```

### Outbound: MESSAGE
Sent on every committed transcript from ElevenLabs STT.
```json
{
  "type": "MESSAGE",
  "conversationId": "conv_01ABC",
  "text": "Sunday bhi open hai?"
}
```

### Outbound: END
Sent when call ends (before session is flushed to DB).
```json
{
  "type": "END",
  "conversationId": "conv_01ABC"
}
```

### Inbound: RESPONSE
AI has replied — convert text to audio and send to customer.
```json
{
  "type": "RESPONSE",
  "conversationId": "conv_01ABC",
  "text": "Haan, Sunday bhi 9am se 9pm tak open hain!"
}
```

### Inbound: KNOWLEDGE_REQUEST
ai-conversation-service lost its session (restart). Resend INIT with knowledge.
```json
{
  "type": "KNOWLEDGE_REQUEST",
  "conversationId": "conv_01ABC"
}
```
**Action:** Look up knowledge from CallSession (already in RAM) → send INIT again.
No REST call needed — knowledge is already in the CallSession.

### Inbound: CALLBACK_NEEDED
AI cannot answer the customer's question.
```json
{
  "type": "CALLBACK_NEEDED",
  "conversationId": "conv_01ABC"
}
```
**Action:** Set `callbackRequested = true` in CallSession. Send a MESSAGE back
to ai-conversation-service with the text:
`"Please tell the customer: Our team will reach you within 24 hours."`

---

## Audio Conversion

Twilio sends audio in mulaw 8kHz (base64). ElevenLabs STT expects PCM 16kHz.
ElevenLabs TTS returns PCM audio. Twilio expects mulaw 8kHz back.

### Inbound (Twilio → ElevenLabs STT)
```
1. Base64 decode → raw mulaw bytes
2. mulaw 8kHz → linear PCM 16-bit 8kHz  (MuLaw decode)
3. Resample 8kHz → 16kHz                 (2x upsample)
4. Send PCM 16kHz to ElevenLabs STT WS
```

### Outbound (ElevenLabs TTS → Twilio)
```
1. Receive PCM audio from ElevenLabs TTS
2. Resample to 8kHz                       (downsample)
3. PCM 16-bit → mulaw                     (MuLaw encode)
4. Base64 encode
5. Wrap in Twilio media JSON frame → send over Twilio WS
```

ElevenLabs STT config:
```
model_id=scribe_v2_realtime
commit_strategy=vad
include_language_detection=true
no_verbatim=true
sample_rate=16000
```

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

### Internal (service-to-service, requires service token with `calls.internal.read`)
| Method | Path | Description |
|---|---|---|
| GET | `/api/internal/calls/{businessId}/recent` | Recent calls for the business — used by dashboard-service |
| GET | `/api/internal/calls/{businessId}/callbacks` | Pending callback list |
| PUT | `/api/internal/calls/{callId}/feedback` | Used by feedback-service after keypress |
| PUT | `/api/internal/calls/{callId}/summary` | Used by conversation-summary-service after AI summary |

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
is created when the WebSocket opens, removed on disconnect (or after a timeout
guard, e.g. 60 minutes — abandoned-session sweeper).

A session carries:
- callId, conversationId (ULID generated on call start), businessId, customerPhone, provider, started-at
- Pre-loaded knowledge text (string, fetched once from knowledge-service — kept for KNOWLEDGE_REQUEST resend)
- Running transcript (list of {speaker, text, timestamp}) — for saving to DB at call end
- WS session reference to `ai-conversation-service` — to send/receive messages
- Flags: `callbackRequested`, `feedbackScore`

**NOT in session:**
- Conversation history — owned entirely by `ai-conversation-service` (in its RAM)

No persistence happens during the call. On disconnect, the session is flushed
to `call_logs` in a single transaction.

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
configs.aiConversationService.wsUrl=ws://localhost:8087/ai-conversation-service/ws/ai
configs.conversationSummaryService.baseUrl=http://localhost:8089/conversation-summary-service
configs.notificationService.baseUrl=http://localhost:8090/notification-service

# pick the active STT / TTS providers
configs.stt.provider=elevenlabs
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
secrets.stt.elevenlabs.apiKey=...
secrets.tts.elevenlabs.apiKey=...
```

---

## Communication With Other Services

| Other service | Why | Direction | Auth |
|---|---|---|---|
| `auth-service` | Issue service tokens | this → auth | clientId/clientSecret |
| `user-business-service` | (Optional) refresh business profile mid-call | this → ubs | service token (`SCOPE_business.internal.read`) |
| `knowledge-service` | Fetch business knowledge text at call start | this → knowledge | service token (`SCOPE_knowledge.internal.read`) |
| `ai-conversation-service` | **Persistent WebSocket** — send INIT/MESSAGE/END, receive RESPONSE/KNOWLEDGE_REQUEST/CALLBACK_NEEDED | this ↔ ai | WS connection (no service token on WS — internal network only) |
| `conversation-summary-service` | Async trigger to generate + save summary after call ends | this → summary | service token (`SCOPE_summary.internal.invoke`) — fire and forget |
| `notification-service` | Async trigger to email the business if a callback was requested | this → notification | service token (`SCOPE_notify.internal.invoke`) — fire and forget |
| `feedback-service` | Inbound: updates feedback after keypress | feedback → this | service token (`SCOPE_calls.internal.write`) |
| `dashboard-service` | Inbound: reads recent calls / callbacks | dashboard → this | service token (`SCOPE_calls.internal.read`) |

The service-to-service policy (which callers can request which scopes) lives
in auth-service's `configs/service.properties`. Update there when this service
goes live.

---

## Async Work (post-call)

- Use Spring `@Async` for MVP (no message queue yet — Kafka/RabbitMQ in Phase 2)
- Two async tasks fire after `call_logs` is persisted on disconnect:
  1. Send transcript → `conversation-summary-service` → which calls
     `ai-conversation-service` and writes the summary back via
     `PUT /api/internal/calls/{id}/summary`
  2. If `callbackRequested == true` → POST to `notification-service` with
     business email + customer phone + brief summary

Failures in async tasks must not affect the call's persistence — log and move on.

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

## What Hasn't Been Built Yet
Everything. Folder + this CLAUDE.md only. Suggested build order:

1. `pom.xml`, `Dockerfile`, `application.properties`, logback
2. App class + `configuration/` (Secrets, Service, DataSource, Security,
   WebSocket — register `/ws/{provider}/call/{callId}` handler)
3. Flyway migration for `calls_schema.call_logs`
4. JPA entity, repository, mapper
5. JWT filter + `BusinessAccessGuard` (copy-adapt from user-business-service)
6. `telephony/TelephonyMediaStreamHandler` + `TwilioMediaStreamHandler`
7. STT + TTS provider interfaces + one impl each
8. `CallSession` + `CallSessionRegistry`
9. `clients/` for downstream REST services (knowledge, summary, notification) +
   `ServiceTokenClient` reused from incoming-call-service pattern
9b. `AiConversationWsClient` — WebSocket client that connects to ai-conversation-service,
    sends INIT/MESSAGE/END, receives RESPONSE/KNOWLEDGE_REQUEST/CALLBACK_NEEDED
10. WebSocket endpoint glue: parse provider from path, dispatch to handler
11. Status-callback controller + post-call persistence + async fan-out
12. Internal + tenant-facing controllers
13. Unit + integration tests (signature validation, session lifecycle,
    tenant isolation in queries)

---

## Reference Patterns
- `incoming-call-service` — provider strategy boilerplate to copy
- `user-business-service/security` — JWT filter + tenant guard
- `auth-service/CLAUDE.md` — JWT claims contract + service-token contract
- ARCH §2.7 — service spec, §4 — full call flow, §5.1 — data isolation rules
