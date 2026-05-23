# CLAUDE.md — ai-conversation-service

## What This Service Does
Stateless AI conversation engine. The **only** service in the platform that
talks to a Large Language Model (Google Gemini today, others tomorrow).
Everything LLM-specific lives behind a strategy interface so the rest of the
platform never imports an SDK.

Primary consumer: `call-orchestration-service`. It opens a **persistent
WebSocket** to this service when a call starts and sends customer utterances
over it; AI replies come back as a single one-shot `RESPONSE` frame per
turn (non-streaming today).

Secondary consumer: `conversation-summary-service`. Calls a one-shot REST
endpoint after a call ends to generate a short call summary.

This service:
- Owns no database
- Holds **in-memory conversation state per active WebSocket** (system prompt,
  message history) — discarded on disconnect
- Does NOT call knowledge-service itself. When a `MESSAGE` arrives before
  the session has knowledge, this service sends a `KNOWLEDGE_REQUEST` back
  to call-orch and queues the message; call-orch responds with an `INIT`
  frame carrying the rendered knowledge blob. The system prompt is then
  built **inside this service** from an internal template + the knowledge
  blob.

---

## Tech Stack
- Java 17, Spring Boot 3.5.x
- Spring WebSocket (`spring-boot-starter-websocket`) — server
- Java HttpClient (or Spring WebClient) for one-shot LLM calls
- Google Gemini HTTP API — wrapped behind `LlmProvider` (default runtime provider)
- Anthropic Java SDK (or HTTP) — wrapped behind `LlmProvider` (peer impl, not default)
- Lombok, ULID, SLF4J + Logback, Caffeine (system-prompt cache per session)
- Port: **8087** (per ARCH §8)

---

## Strict Conventions — Follow These Exactly

Mirror what user-business-service / knowledge-service already do:

- ULIDs for ids (conversationId, requestId). Sessions are keyed by `conversationId` — do NOT introduce a separate `sessionId`.
- Config split: `configs/service.properties` + `secrets/secrets.properties`
- DTOs separate from any domain types
- Lombok: `@Value @Builder @Jacksonized` for responses, `@Data` for requests
- `@RequiredArgsConstructor` everywhere
- Custom exceptions → `GlobalExceptionHandler` → `ApiError`
- Service-token validation for `/api/internal/**` endpoints
- WebSocket upgrade validates a service token from `Authorization: Bearer …`
  header (no JWT; call-orch authenticates with its own service token)
- No tenant guard here — this service trusts the `businessId` the caller passes
  on the WS START frame. Tenant isolation is enforced upstream (call-orch
  has already verified the call belongs to that business). Document this
  explicitly so we don't slip into thinking otherwise.

---

## Provider Strategy Pattern — Required

Same pattern incoming-call-service / call-orchestration-service use.

### `llm/LlmProvider`
```java
public interface LlmProvider {
    String id();                                              // "gemini", "anthropic", "openai"
    /** One-shot completion. Used by both the WS handler (per MESSAGE) and /summarise. */
    LlmReply complete(LlmRequest request);
    /** Streaming variant — kept on the interface for future use; not called today. */
    Flow.Publisher<LlmDelta> streamReply(LlmRequest request);
}
```

`LlmRequest` is provider-agnostic: `systemPrompt`, `messages` (list of
`{role, content}`), `maxTokens`, `temperature`, `tools` (optional, Phase 2).

`LlmReply` is the one-shot result: `text` (full), `finishReason`, `usage`.

`LlmDelta` is the streaming chunk: `text` (partial), `finishReason` (null
until last frame), `usage` (only on last frame).

### `llm/gemini/GeminiLlmProvider` (default)
- Only class allowed to `import com.google.genai.*` (or call Gemini HTTP)
- Translates `LlmRequest` → Gemini `generateContent` request:
  `systemPrompt` → `system_instruction`; `messages[]` → `contents[]`
  (role `user` / `model`)
- Implements `complete` against `:generateContent`; `streamReply` against
  `:streamGenerateContent` (unused today, keep working)

### `llm/anthropic/AnthropicLlmProvider` (peer impl)
- Only class allowed to `import com.anthropic.*`
- Translates `LlmRequest` → Anthropic Messages API request
- Implements `complete` against the non-streaming Messages API; `streamReply`
  via SSE (`stream=true`)
- Not the runtime default — included so the strategy interface stays honest

### Registry
`LlmProviderRegistry` injects `List<LlmProvider>`. Active provider is chosen
per request via `configs.llm.defaultProvider` (default: `gemini`) OR an
explicit `provider` field on the request payload (caller override for A/B
tests).

---

## WebSocket Protocol — `/ws/conversation/{conversationId}`

call-orchestration-service opens this WS when a call starts and closes it
when the call ends. Frames are JSON. A single `WsMessageType` enum is used
in both directions.

- **Inbound (call-orch → ai-conv):** `INIT`, `MESSAGE`, `END`
- **Outbound (ai-conv → call-orch):** `RESPONSE`, `KNOWLEDGE_REQUEST`, `CALLBACK_NEEDED`

Sessions are keyed by `conversationId` (not `sessionId`). The protocol is
**non-streaming**: one `RESPONSE` per inbound `MESSAGE`. The WS handler calls
`LlmProvider.complete()`, not `streamReply()`.

### Inbound (from call-orch)

`INIT` — sent either at WS open OR in reply to a `KNOWLEDGE_REQUEST`. Carries
the rendered `knowledge` blob:
```json
{
  "type": "INIT",
  "conversationId": "01HX…",
  "businessId":     "01KR…",
  "callId":         "CA…",
  "knowledge": "=== BUSINESS PROFILE … (rendered knowledge text from knowledge-service) ===",
  "metadata": { "customerPhone": "+91…", "language": "en" }
}
```
On INIT, ai-conv builds the session's `systemPrompt` internally from a
prompt template + the `knowledge` blob.

`MESSAGE` — customer utterance (final STT transcript, not interim):
```json
{ "type": "MESSAGE", "conversationId": "01HX…", "messageId": "01HX…", "text": "What are your timings?" }
```
If `MESSAGE` arrives **before** `INIT` (no knowledge yet for this session),
ai-conv enqueues it and emits a `KNOWLEDGE_REQUEST`. Once `INIT` arrives,
queued messages are processed in order.

`END` — call-orch closes the WS naturally; preferred so we can flush usage stats:
```json
{ "type": "END", "conversationId": "01HX…" }
```

### Outbound (from this service)

`KNOWLEDGE_REQUEST` — sent when a `MESSAGE` arrives without prior `INIT`:
```json
{ "type": "KNOWLEDGE_REQUEST", "conversationId": "01HX…" }
```

`RESPONSE` — one per `MESSAGE`, full reply text (non-streaming):
```json
{
  "type": "RESPONSE",
  "conversationId": "01HX…",
  "replyToMessageId": "01HX…",
  "text": "Sure, we're open …",
  "finishReason": "stop",
  "usage": { "inputTokens": 412, "outputTokens": 38 }
}
```

`CALLBACK_NEEDED` — emitted **instead of** `RESPONSE` when the LLM output
is the literal string `CALLBACK_NEEDED` (i.e. the model decided a human
callback is required). Call-orch handles the callback flow:
```json
{
  "type": "CALLBACK_NEEDED",
  "conversationId": "01HX…",
  "replyToMessageId": "01HX…",
  "usage": { "inputTokens": 412, "outputTokens": 4 }
}
```

Fatal errors close the WS with an appropriate close code:
- `4001` invalid INIT
- `4002` missing/invalid service token
- `4003` businessId/conversationId mismatch across frames
- `1011` server-side LLM error after retry exhausted

(Non-fatal LLM transients are still retried inline; if you need to surface
a soft error, extend the outbound enum — do not silently reintroduce a
generic `ERROR` frame without updating call-orch's client.)

### State per session (in memory)
```
conversationId, businessId, callId
knowledge              (raw blob set on INIT)
systemPrompt           (built from template + knowledge on INIT)
messages: List<Msg>    (role=user|assistant, text)
pendingMessages        (queued while awaiting INIT after KNOWLEDGE_REQUEST)
usage    : TokenUsage  (running totals)
provider : LlmProvider (chosen at INIT; default = gemini)
createdAt, lastActivityAt
```

A `SessionRegistry` Spring bean holds `Map<conversationId, ConversationSession>`.
A scheduled sweeper closes sessions idle > 30 min as a leak guard.

---

## REST Endpoints

### Service-to-service (require service token; `SCOPE_ai.internal.invoke`)

| Method | Path | Description |
|---|---|---|
| POST | `/api/internal/ai/respond` | One-shot reply (no streaming). Used for tests, fallback, or non-voice channels later. Body: `{businessId, systemPrompt, messages[], provider?}` → `{text, usage}`. |
| POST | `/api/internal/ai/summarise` | Generate call-summary text. Body: `{businessId, transcript:[{speaker,text}], maxTokens?}` → `{summary, queryType?, usage}`. Called by `conversation-summary-service` post-call. |
| GET | `/api/internal/ai/sessions/{conversationId}` | (Debug) introspect an active session — message count, token usage, age. |

### Health
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/health` | Standard UP response |

---

## Configuration Required

### `configs/service.properties`
```
configs.authService.baseUrl=http://localhost:8081/auth-service

configs.llm.defaultProvider=gemini
configs.llm.maxOutputTokens=512
configs.llm.temperature=0.3
configs.llm.requestTimeoutSeconds=20
configs.llm.streamingTimeoutSeconds=60

configs.session.idleTimeoutMinutes=30
configs.session.maxConcurrent=500
```

### `secrets/secrets.properties`
```
# JWT verification (must match auth-service) — used to validate the service
# token that call-orchestration-service presents on the WebSocket upgrade
secrets.jwt.secret=...
secrets.jwt.type=Bearer
secrets.jwt.expected-audience=ai-conversation-service

# auth-service client creds (this service is a client of /api/internal/token
# if it ever needs to call other services; today it does NOT).
secrets.authService.clientId=ai-conversation-service
secrets.authService.clientSecret=...

# LLM vendor keys — only the provider's own class touches these
# Gemini is the runtime default
secrets.llm.gemini.apiKey=...
secrets.llm.gemini.model=gemini-2.5-flash
# Anthropic peer impl (not default, but registered)
secrets.llm.anthropic.apiKey=
secrets.llm.anthropic.model=claude-sonnet-4-5
# Future providers:
# secrets.llm.openai.apiKey=...
# secrets.llm.openai.model=gpt-5
```

---

## Security

- **WebSocket upgrade** (`/ws/conversation/{sessionId}`): the upgrade HTTP
  request must carry `Authorization: Bearer <service token>` with audience
  matching `ai-conversation-service` and scope `ai.internal.invoke`. Reject
  the upgrade with HTTP 401 otherwise — never let an unauthenticated socket
  open.
- **REST `/api/internal/**`**: same service-token requirement.
- **Tenant isolation**: this service does NOT verify the JWT's `businessId`
  matches anything — call-orch and the summary service are trusted upstream
  and pass `businessId` as data, not as an authorisation claim.
- **No tenant data is persisted** — sessions are in-memory. On disconnect or
  process restart, all state is dropped. Acceptable because the durable
  transcript lives in call-orchestration-service's `call_logs`.

---

## Failure Modes & Recovery

| Failure | Behaviour |
|---|---|
| LLM 429 / 5xx (transient) | Retry once with exponential backoff (200ms→800ms); on second failure close WS with 1011 and let caller reconnect/retry |
| LLM context window exceeded | Truncate oldest non-system messages to fit; if still too large, close WS with 1011 |
| LLM call > requestTimeoutSeconds | Abort the call, close WS with 1011; leave caller to reopen |
| Session idle > 30 min | Server closes WS with 1000 |
| WS dropped mid-reply | Caller is responsible for reconnect; this service treats the session as dead and discards state |
| `MESSAGE` before `INIT` | Send `KNOWLEDGE_REQUEST`, queue the message; process once `INIT` arrives |
| Provider not configured | Service refuses INIT with close code 4001, message "provider X not configured" |

All failures are logged with `conversationId` + `callId` for cross-service tracing.

---

## Communication With Other Services

| Other service | Why | Direction | Auth |
|---|---|---|---|
| `auth-service` | None at runtime — JWT secret is shared via config | — | — |
| `call-orchestration-service` | Opens WS + sends user messages, receives streaming replies | call-orch → this | service token (`SCOPE_ai.internal.invoke`) on WS upgrade |
| `conversation-summary-service` | Calls `/api/internal/ai/summarise` post-call | summary → this | service token (`SCOPE_ai.internal.invoke`) |
| LLM vendor (Anthropic etc.) | The actual LLM call | this → vendor | API key |

Update `auth-service/configs/service.properties` to add this entry before
going live:
```
configs.auth.policy.services.ai-conversation-service.scopes=ai.internal.invoke
```
And in `auth-service/secrets/secrets.properties` add:
```
secrets.services.ai-conversation-service.id=ai-conversation-service
secrets.services.ai-conversation-service.password=...
```

---

## Why a Persistent WebSocket and Not REST

ARCH §2.8 originally specified REST (`POST /ai/respond` per turn). We are
overriding that for three reasons:

1. **Conversation history overhead**. With REST, call-orch would have to
   resend the full `messages[]` array on every turn. With WS, history lives
   in this service's memory for the call's duration.
2. **Knowledge-on-demand handshake**. The `KNOWLEDGE_REQUEST` →  `INIT`
   exchange is a natural fit for a bidirectional channel — call-orch
   doesn't need to know up front whether knowledge is required before
   opening the session.
3. **Future barge-in / streaming**. Even though today's protocol is
   non-streaming one-shot `RESPONSE` per `MESSAGE`, keeping the WS open
   lets us add `CANCEL` and (re)enable streaming later without a transport
   change.

REST endpoints remain for the post-call summary path and for non-voice
channels (chat, future SMS bots).

---

## What Hasn't Been Built Yet
Everything. Folder + this CLAUDE.md only.

### Build Order
1. `pom.xml`, `Dockerfile`, `application.properties`, logback (copy from knowledge-service skeleton)
2. App class + `configuration/` (Secrets, Service, Security, WebSocket)
3. Service-token verification: reuse the `security/token/*` + `JwtAuthenticationFilter` shape from knowledge-service (verify-only, no business guard)
4. `llm/` package: `LlmProvider`, `LlmRequest`, `LlmReply`, `LlmDelta`, `LlmProviderRegistry`
5. `llm/gemini/GeminiLlmProvider` — one-shot `complete` (default); streaming method present but unused
6. `llm/anthropic/AnthropicLlmProvider` — peer impl, both streaming and one-shot
7. `session/ConversationSession` + `SessionRegistry` (keyed by `conversationId`) + idle sweeper
8. WS protocol DTOs — single `WsMessageType` enum (`INIT`, `MESSAGE`, `END`, `RESPONSE`, `KNOWLEDGE_REQUEST`, `CALLBACK_NEEDED`) and a typed envelope per frame
9. `ConversationWebSocketHandler` — wires inbound frames → session → LLM provider → outbound frames; handles `KNOWLEDGE_REQUEST` queueing + `CALLBACK_NEEDED` detection
10. WebSocket security: token check during `beforeHandshake`; reject on missing/invalid
11. REST controllers: `InternalAiController` (`/respond`, `/summarise`, `/sessions/{conversationId}`) + `HealthController`
12. `GlobalExceptionHandler`, `ApiError`, custom exceptions
13. Tests: provider routing (registry), WS lifecycle (INIT → MESSAGE → RESPONSE), pre-INIT MESSAGE → KNOWLEDGE_REQUEST → INIT replay, `CALLBACK_NEEDED` detection, idle sweeper, summary one-shot

---

## Phase 2 (Not MVP)
- **Streaming replies** — switch the WS handler from `complete()` to
  `streamReply()` and replace single `RESPONSE` with `RESPONSE_DELTA` +
  `RESPONSE_DONE` frames. Provider impls already support it.
- **Tool calling** — pass `tools[]` in `LlmRequest`. Tool execution dispatch
  back to call-orch via a new `TOOL_CALL` outbound frame; call-orch resolves
  (e.g. "lookup appointment", "create callback") and sends `TOOL_RESULT`.
- **Barge-in handling** — inbound `CANCEL` frame to abort the current call.
- **Multi-provider A/B testing** — log provider id + usage per session for cost/quality comparison.
- **Local model fallback** — add `LocalLlmProvider` (Llama via vLLM) as a cheaper warm-pool option for low-stakes turns.
- **Embeddings + RAG** — only if knowledge-service starts shipping documents.
  Add an `embeddings/` strategy in this service or factor into a new
  `retrieval-service`. Decide at the time.

---

## Reference Patterns
- `incoming-call-service/CLAUDE.md` — provider strategy + signature-on-upgrade pattern
- `call-orchestration-service/CLAUDE.md` — peer service that opens the WS to this one; see how it expects to consume the streaming protocol
- `knowledge-service/CLAUDE.md` — produces the rendered text that becomes our `systemPrompt`
- `auth-service/CLAUDE.md` — service-token contract
- ARCH §2.8 — original (REST-only) design, intentionally overridden here
