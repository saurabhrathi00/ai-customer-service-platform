# VoxAI Dashboard

The business-side dashboard for the AI customer service platform. Lets SMB
owners sign in, manage the knowledge their AI agent uses, and review call
activity.

## Quick start

```bash
cd frontend
npm install
npm run dev    # http://localhost:5173
```

In dev, `/api/<service>/...` requests are proxied to the local Spring services
via `vite.config.ts`. No CORS config needed.

```bash
npm run build    # type-check + production build
npm run preview  # serve dist/
```

## Stack

- **Vite** + **React 19** + **TypeScript** (strict)
- **Tailwind CSS** + design-token-based theming
- **React Router v6** (data router)
- **TanStack Query** for server state
- **Zustand** for client state (auth, theme)
- **react-hook-form** + **zod** for forms
- **recharts** for the dashboard chart
- **lucide-react** icons, **sonner** toasts, **framer-motion** ready for richer animation

## Pages

| Path             | What lives there                                            |
|------------------|-------------------------------------------------------------|
| `/login`         | Sign-in form                                                |
| `/register`      | Create a new business + auto sign-in                        |
| `/`              | Dashboard — KPIs, calls/day chart, recent calls             |
| `/calls`         | Filterable + searchable calls list (all / callbacks / hot)  |
| `/calls/:id`     | Call detail — summary, transcript, signals                  |
| `/knowledge`     | Profile / FAQs / Free-form / Escalation rules               |
| `/settings`      | Business profile, phone numbers, interest-scoring config    |

## Folder layout

```
src/
├── api/              # axios client + per-resource fns
├── components/
│   ├── ui/           # primitives (Button, Card, Input, Dialog, …)
│   └── app/          # shell + provider components
├── features/         # tab/page-specific composed UI
├── lib/              # cn, jwt, formatters
├── routes/           # page components + router
├── store/            # zustand stores
└── types/            # shared TS types (matches backend DTOs)
```

## Rules + constraints

See [`FRONTEND-RULES.md`](./FRONTEND-RULES.md). Don't break them.

The full backend contract is in [`../frontend-api-spec.md`](../frontend-api-spec.md).

## Known backend gaps (flag before integrating end-to-end)

1. **Scope mismatch** — MVP signin grants `business.read/write` only, but
   knowledge-service and call-orch endpoints require `knowledge.*` /
   `calls.read`. Every dashboard call will 403 until backend widens
   `AuthenticationService.MVP_SCOPES`.
2. **CORS off everywhere** — fine in dev (we proxy through Vite), but for
   production a `CorsConfigurationSource` bean (or Caddy origin rules) is
   needed.
3. **No tenant `/callbacks` endpoint** — we filter `callbackRequested === true`
   client-side from `/calls/{id}/recent`. Fine for MVP scale.
4. **No per-call GET** — the detail page reuses the recent-calls list. Add
   a dedicated endpoint when call counts get large.
