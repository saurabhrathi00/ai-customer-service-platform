# ai-customer-service-platform

A voice AI customer service platform. A caller dials a Twilio number, talks to
an LLM-driven assistant over a real phone call, and the assistant answers from
the business's own knowledge — falling back to a human callback when it can't.

## What the platform does

1. Customer calls a Twilio number assigned to a business.
2. Twilio hits **incoming-call-service** which validates the request, looks up
   the business, and returns TwiML telling Twilio to open a Media Stream
   WebSocket to **call-orchestration-service**.
3. **call-orchestration-service** drives the live call:
   - Streams caller audio to a Speech-to-Text provider (ElevenLabs Scribe or
     Deepgram Flux).
   - Forwards each finalised utterance over a WebSocket to
     **ai-conversation-service**.
   - Receives streaming LLM replies (Gemini today; Anthropic Haiku peer impl
     in tree), streams them through ElevenLabs TTS, and sends mu-law audio
     back to Twilio in real time.
   - Handles barge-in (caller interrupts → bot stops), graceful hangup with
     full-playback-before-drop, and persists the call log post-call.
4. **ai-conversation-service** is the only service that talks to an LLM. It
   maintains the in-memory chat history per call and renders the system
   prompt from the business knowledge blob.
5. **knowledge-service** renders a per-business knowledge blob from Postgres
   + Qdrant on call start, cached for the call's lifetime.
6. **user-business-service** owns the tenant/business directory; phone-number
   → business resolution lives here.
7. **auth-service** issues short-lived JWT service tokens for inter-service
   calls (`SCOPE_*.internal.*`).

After the call ends, call-orchestration persists the transcript to
`calls_schema.call_logs` and asynchronously fires summary + callback
notification tasks.

## Services and ports

| Service | Port | Role |
|---|---:|---|
| `auth-service` | 8081 | JWT signer / verifier, service-token issuance |
| `user-business-service` | 8082 | Business directory, Twilio-number → business lookup |
| `knowledge-service` | 8083 | Renders the per-business knowledge blob |
| `incoming-call-service` | 8085 | Twilio voice webhook — returns Media Stream TwiML |
| `call-orchestration-service` | 8086 | Live-call brain: STT ↔ ai-conv ↔ TTS, persists `call_logs` |
| `ai-conversation-service` | 8087 | LLM gateway, in-memory conversation state |
| `caddy` / `caddy-local` | 80/443 / 8080 | Reverse proxy in front of all services |

External dependencies: Twilio (telephony), Google Gemini (LLM), ElevenLabs
(STT + TTS) or Deepgram Flux (STT), Supabase Postgres, Qdrant (vector store),
and an `embedding-service` (Python, separate repo) for knowledge embeddings.

## Repository layout

```
ai-customer-service-platform/
├── auth-service/
├── user-business-service/
├── knowledge-service/
├── ai-conversation-service/
├── incoming-call-service/
├── call-orchestration-service/
├── docker-compose.yml          # production-shape, gated by `production` profile
├── docker-compose.local.yml    # local-dev overlay (plain-HTTP Caddy, /etc/ mounts)
├── Caddyfile                   # production (Let's Encrypt)
└── Caddyfile.local             # local (plain HTTP for ngrok / cloudflared)
```

Each Spring Boot service has the same shape:

```
<service>/
├── pom.xml
├── Dockerfile
├── CLAUDE.md                   # service-specific design notes
├── src/main/java/com/aiassistant/<service>/...
├── src/main/resources/
│   ├── application.properties  # classpath defaults (port, JPA, etc.)
│   └── logback-spring.xml
├── configs/
│   └── service.properties      # non-secret runtime config (bind-mounted)
└── secrets/
    └── secrets.properties      # API keys, DB password (gitignored)
```

`secrets/` is `.gitignore`d. Each service expects its own `secrets.properties`
to be present locally — populate from your password manager / 1Password / etc.

---

## Running locally

### Prerequisites

- Docker Desktop (or any Docker Compose v2 runtime)
- A public HTTPS tunnel — **ngrok** (recommended) or **cloudflared**
- Twilio account + a phone number for testing
- Provider keys: Gemini, ElevenLabs (and/or Deepgram), Twilio auth token

### 1. Fill in `secrets.properties` for every service

Each service needs its own `secrets/secrets.properties`. The most important
ones:

```properties
# auth-service/secrets/secrets.properties
secrets.jwt.secret=...
secrets.services.<other-service>.password=...

# user-business-service/secrets/secrets.properties
secrets.datasource.username=...
secrets.datasource.password=...
secrets.jwt.secret=...
secrets.authService.clientId=user-business-service
secrets.authService.clientSecret=...

# call-orchestration-service/secrets/secrets.properties
secrets.datasource.username=...
secrets.datasource.password=...
secrets.jwt.secret=...
secrets.twilio.account-sid=...
secrets.twilio.auth-token=...
secrets.elevenlabs.key=...
secrets.deepgram.key=...

# ai-conversation-service/secrets/secrets.properties
secrets.jwt.secret=...
secrets.llm.gemini.apiKey=...
secrets.llm.gemini.model=gemini-2.5-flash-lite

# incoming-call-service/secrets/secrets.properties
secrets.twilio.auth-token=...
secrets.authService.clientId=incoming-call-service
secrets.authService.clientSecret=...
```

`secrets.jwt.secret` must match across services that verify it.

### 2. Start the tunnel

```bash
ngrok http 8080
# copy the https:// URL — you'll need it
```

Caddy in local mode listens on host `:8080`, plain HTTP. ngrok terminates TLS
at the edge.

### 3. Bring up the stack

```bash
PUBLIC_HOST=localhost \
ACME_EMAIL=dev@example.com \
CONFIGS_CALLORCHESTRATION_WSBASEURL="wss://<your-ngrok-host>/call-orchestration-service" \
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

The two `PUBLIC_HOST` / `ACME_EMAIL` vars are for the production `caddy`
service which is gated behind the `production` profile and not started here
— they're only required because Compose interpolates env vars at parse time.

The `CONFIGS_CALLORCHESTRATION_WSBASEURL` is the URL Twilio will dial for the
Media Stream — it must be reachable from Twilio's edge, so it must be your
tunnel URL with `wss://` scheme.

### 4. Configure the Twilio phone number

In the Twilio Console, on the phone number:

| Field | Value |
|---|---|
| **Voice → A call comes in** | `https://<your-ngrok-host>/incoming-call-service/api/v1/webhook/twilio/incoming/call` (HTTP POST) |

### 5. Place a test call

Dial the Twilio number from a real phone. The bot greets you and the call
flow begins.

Tail the most useful logs:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml \
  logs -f --tail=0 call-orchestration-service ai-conversation-service
```

Or just the latency timeline:

```bash
docker logs -f call-orchestration-service 2>&1 | grep -E "latency|barge|hangup"
```

### Rebuilding after a code change

The `configs/service.properties` and `secrets/secrets.properties` files are
bind-mounted under `/etc/` inside each container — edit + `docker compose
restart <service>` is enough. Java code changes need `up -d --build <service>`.

### Switching STT / LLM provider

| Provider knob | Where |
|---|---|
| STT (ElevenLabs vs Deepgram) | `call-orchestration-service/configs/service.properties` → `configs.stt.provider=elevenlabs` or `deepgram` |
| LLM model | `ai-conversation-service/secrets/secrets.properties` → `secrets.llm.gemini.model=...` |
| Barge-in sensitivity | `configs.stt.bargeInMinLengthChars` and `configs.stt.confidenceThreshold` |
| Hangup tail | `configs.telephony.hangupTailMs` |
| ElevenLabs manual-commit silence | `configs.elevenlabs.sttManualCommitSilenceMs` |
| Deepgram model | `configs.deepgram.sttModelId` |

All are picked up by `restart`, no rebuild needed.

---

## Running in production

### Prerequisites

- A VM / instance with Docker installed
- A domain pointing to it (Caddy will Let's-Encrypt itself)
- All `secrets.properties` filled with production values (not the dev keys)

### Bring-up

```bash
PUBLIC_HOST=your-domain.com \
ACME_EMAIL=ops@your-company.com \
CONFIGS_CALLORCHESTRATION_WSBASEURL="wss://your-domain.com/call-orchestration-service" \
docker compose --profile production up -d --build
```

The `production` profile enables the `caddy` service which:

- Terminates TLS using Let's Encrypt (auto-renews).
- Reverse-proxies all paths to the relevant Spring service on the `backend`
  Docker network.

Twilio webhook for the production number then points at
`https://your-domain.com/incoming-call-service/api/v1/webhook/twilio/incoming/call`.

### Operational notes

- **Logs**: each service writes structured logs to stdout. The latency
  timeline is grepable: `grep "\[latency\]"`.
- **DB migrations**: Flyway runs on startup for services that own a schema
  (`auth-service`, `user-business-service`, `knowledge-service`,
  `call-orchestration-service`).
- **Schemas owned**: `auth_schema`, `business_schema`, `knowledge_schema`,
  `calls_schema`. Cross-schema reads are deliberate; cross-schema writes are
  forbidden — go through the owning service.
- **Zero-downtime rolling restart**: `docker compose up -d --build
  <service>` recreates one service; everything else stays connected through
  the `backend` network. Be aware that mid-call state lives in
  call-orchestration-service memory — restarting it drops live calls.

### Required environment variables (production)

| Variable | Purpose |
|---|---|
| `PUBLIC_HOST` | Domain that Caddy serves (Let's Encrypt cert is issued for this) |
| `ACME_EMAIL` | Contact email for Let's Encrypt |
| `CONFIGS_CALLORCHESTRATION_WSBASEURL` | Public wss URL Twilio dials for the Media Stream (`wss://$PUBLIC_HOST/call-orchestration-service`) |

Anything else in `configs/service.properties` can be overridden via the env
vars listed at the top of each file (e.g.
`CONFIGS_AUTHSERVICE_BASEURL=…`).

---

## Architecture (call flow)

```
   ┌───────┐                                                    ┌─────────────────────┐
   │Twilio │ ─── POST  /incoming-call-service/api/v1/webhook ─► │ incoming-call-svc   │
   │caller │ ◄── TwiML <Connect><Stream url=wss://…/ws/twilio> ─│ (validates,         │
   └───┬───┘                                                    │  resolves business) │
       │                                                        └─────────────────────┘
       │  Media Stream (mu-law @ 8 kHz, bidirectional)
       ▼
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │ call-orchestration-service                                                   │
   │                                                                              │
   │  inbound audio ──► STT (ElevenLabs Scribe v2 realtime  OR  Deepgram Flux)    │
   │                            │                                                 │
   │                            ▼ final transcript                                │
   │                   ┌─────────────────────────┐                                │
   │                   │ ai-conversation-service │ ──► Gemini Flash / Flash-Lite  │
   │                   │ (WebSocket per call)    │     (streaming SSE)            │
   │                   └─────────────────────────┘                                │
   │                            │ streaming reply text                            │
   │                            ▼                                                 │
   │                   TTS (ElevenLabs eleven_turbo_v2_5, streamed)               │
   │                            │ mu-law chunks                                   │
   │                            ▼                                                 │
   │  outbound audio ◄── back to Twilio Media Stream                              │
   │                                                                              │
   │  on call-end → persist transcript to Postgres `calls_schema.call_logs`       │
   │                fire async tasks (summary, callback notification)             │
   └──────────────────────────────────────────────────────────────────────────────┘
```

## License

Proprietary. All rights reserved.
