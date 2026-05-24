# Backlog — explicitly deferred work

Things we discussed and **chose not to build for MVP**, captured here so we
don't lose the context. Sorted by feature, not priority.

When picking one up, read the matching section for the *why* — most of these
have non-obvious tradeoffs that drove the deferral.

---

## Lead handoff workflow

### SSE-based real-time UI updates
Currently the dashboard polls `/leads` every 30s. Owner sees a new lead at
most 30s late. Plan when we upgrade:

- New endpoint on **user-business-service**:
  `GET /api/v1/business/{id}/leads/stream` returning `text/event-stream`
- Spring `SseEmitter` per connection, indexed by businessId in
  `Map<String, Set<SseEmitter>>`
- `LeadService` publishes `lead.changed` events on create / approve / decline /
  ignore / reminder-sent
- Event payload is a "refetch trigger" only — frontend re-runs the existing
  TanStack Query, doesn't merge state
- 25-second heartbeat ping so corporate proxies don't kill idle connections
- 30-min server-side emitter timeout; browser `EventSource` auto-reconnects

**Auth gotcha** (the real reason this is deferred — needs a decision):
`EventSource` can't set custom headers. Three paths:
1. `fetch()` + `ReadableStream` + manual SSE parser (~30 lines, cleanest)
2. Token in `?access_token=` query param (zero code, but token in URL / proxy logs)
3. `@microsoft/fetch-event-source` lib (~3 KB, EventSource-style API + headers)

MVP keeps polling. Drop in SSE when owners complain about latency.

### Native browser notifications
Push to the OS even when the tab is closed/backgrounded. Requires
`Notification.requestPermission()` + a service worker that subscribes to
the SSE stream. Useful once SSE lands.

### Multi-recipient WhatsApp
Today one owner WhatsApp number per business (on `businesses.whatsapp_number`).
Was specced as a multi-recipient table but cut for MVP. When we add it:

- New table `business.notification_recipients` (business_id, phone, label,
  is_active)
- Settings UI to add/remove numbers + labels (Owner, Receptionist, Manager)
- Notification-service fan-out: send to ALL active recipients
- First-to-act wins via Postgres advisory lock on the lead row
- Other recipients get a "marked as confirmed by Rahul" update

### Signed deep-link WhatsApp actions (skip dashboard login)
Today the WhatsApp link opens the dashboard which requires a JWT login.
Future: signed short-lived token in the link → public endpoint on
notification-service that decodes it → confirms/declines the lead without
the owner needing to log in. Faster but requires careful token scoping.

### `IGNORED` reminder behaviour audit
We rely on the lead's terminal state to stop reminders. Edge case: if the
owner ignores via mobile-dashboard but the WhatsApp scheduler has already
queued the next reminder send, there's a small window where one extra ping
could fire. Probably fine; if owners complain, add an explicit "skip if
status changed since enqueue" check at send time.

---

## Appointment booking (Phase 2 of the original spec, intentionally cut)

We pivoted from "AI books appointments with calendar" to "AI raises a lead,
owner decides outside the system." The original deeper spec is here so we
remember the path forward.

### Quick-block slots
Owner takes a walk-in offline → opens dashboard → one-tap blocks an upcoming
slot so AI doesn't suggest it. Needs:
- `business.blocked_slots` table (business_id, slot_start, slot_end, label)
- "Quick block" button on /leads + a calendar week-view widget
- Lead-extraction prompt should be aware of blocked slots when guessing
  `suggestedDatetime`

### Google Calendar integration (Phase 2)
- OAuth flow per business (one-time setup)
- Token refresh handling
- On approve(appointment) → create event in business's GCal
- Read free/busy at suggest time so the AI offers slots that won't conflict
- Multi-staff routing (which calendar?) — needs a `calendars[]` model

### Mid-call free/busy lookup (Phase 3)
Once GCal is wired, let the AI offer actual open slots during the call
(rather than just collecting "Wednesday morning"). Big complexity bump:
timezone handling, mid-call latency budget (~1-2s extra per turn), multi-
staff selection. Only do this if owners specifically demand it.

### `.ics` attachment in confirmation WhatsApp
After approve, attach an `.ics` file so customer / owner can one-tap add
the event to any calendar (Google, Apple, Outlook). Works without OAuth.
Smart middle-ground if GCal feels too heavy.

---

## Voice pipeline (call-orchestration-service)

### ElevenLabs VAD mode auto-reconnect
We use ElevenLabs STT in `manual` commit mode because `vad` mode randomly
clean-closes the WS at ~24s ([livekit/agents#4087](https://github.com/livekit/agents/issues/4087)).
Plan if we want to try VAD again: detect 1000-close mid-call → auto-reconnect
the STT session → buffer audio during the gap. Risky; current manual mode
is reliable enough.

### Gemini 503 retry / fallback
Today the LLM error path triggers our canned fallback message. Better:
- One retry with backoff before the fallback
- OR fall through to the Anthropic provider (we have it registered) for
  the second attempt
Provider routing per-request is already supported by the `LlmProvider`
strategy; just needs wiring.

### Knowledge fetch slowness
We saw ~14s on initial knowledge-service GET in some runs. Untriaged. When
investigating: check connection pool sizing, DB latency, JSON serialisation
cost of large `servicesOffered[]` arrays.

---

## Auth + tenant API

### Pagination
No list endpoint paginates. Today `GET /calls/{id}/recent` and
`GET /leads` return everything. Add cursor pagination with sensible
defaults (50 items, `nextCursor` in response) before any business has
> ~500 calls / leads.

### Per-call GET endpoint
`/calls/:id` detail page reuses the recent list. Add a dedicated
`GET /api/v1/calls/{businessId}/{callId}` so the URL works even when the
call is older than the "recent" window.

### Tenant `/callbacks` endpoint
Today the frontend filters `callbackRequested === true` client-side.
Cheap server-side filter: `GET /api/v1/calls/{businessId}/callbacks`.

### Logout endpoint + refresh-token revocation
`POST /auth/logout` currently just drops tokens client-side. Server-side
revocation needs a refresh-token denylist (Redis or a small DB table).
Required before a real production launch but skipped for MVP.

### `aud` claim enforcement on user JWTs
Service tokens carry `aud`; user tokens don't. Each downstream service
should verify `aud` matches its own service name. Coordinated change
across all services that verify JWTs.

---

## Frontend polish

### shadcn `Sheet` / `DropdownMenu`
Hand-rolled `Dialog` works. Replacing with Radix primitives (via shadcn)
would give us free animation + a11y wins. Not blocking.

### Optimistic mutations
Approve / decline / ignore could update the UI immediately and roll back
on error. Today shows a loading spinner. Nice-to-have UX bump.

### Empty states with illustrations
`EmptyState` is plain text + icon today. Designer-led illustrations would
make zero-state screens feel intentional.

---

## Infrastructure / prod readiness

### CORS configuration
All services have `cors.disable()`. Vite dev proxy sidesteps this in dev.
For prod (frontend on different origin than the API gateway), add a
`CorsConfigurationSource` bean per service OR add origin rules in Caddy.

### Real refresh-token rotation
Today the refresh token is not rotated on use — same token until it
expires. Best practice is to issue a new refresh token on every use and
invalidate the old one. Requires the revocation store from above.

### Healthcheck endpoints in docker-compose
Most services don't have a `healthcheck:` clause. `depends_on:
condition: service_healthy` only works if the upstream actually defines
one. Add `curl /api/v1/health` healthchecks across the board.
