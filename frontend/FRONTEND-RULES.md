# Frontend Rules — AI Customer Service Platform

These rules govern every change inside `/frontend`. They were set by the user
and must persist across sessions. Read this file before doing any frontend
work. Update it when the user changes the rules.

## What this product is
The phone-side of the platform answers business calls with an AI agent. This
**frontend is the dashboard** that SMB owners (the businesses, not the phone
callers) use to:
1. Log in
2. See call activity, summaries, transcripts, callback list
3. Manage the knowledge their AI agent uses to answer questions
4. Configure phone numbers and rating thresholds

## Quality bar
> "as if its done by a 20 years experienced frontend engineer"

Concretely:
- TypeScript strict mode. No `any` in committed code.
- Component-driven. Reusable primitives in `src/components/ui`.
- Every async surface has: loading skeleton, empty state, error state.
- Keyboard-accessible (tab order, focus rings, ARIA where needed).
- Responsive — mobile/tablet/desktop layouts all work.
- Subtle animations (Framer Motion / CSS transitions), no garish ones.
- Dark + light theme.
- Loading states under 200ms feel instant — use optimistic UI for mutations.
- Real, considered empty states with CTAs ("No calls yet. Share your number…").
- Forms use react-hook-form + zod schemas. Inline validation, never alerts.
- No console errors in production build.

## Stack (chosen for senior-engineer vibes + speed)
- **Vite** + **React 18** + **TypeScript**
- **Tailwind CSS** + **shadcn/ui** (Radix primitives, copy-paste components)
- **React Router v6** (data routers, loaders where it helps)
- **TanStack Query** for server state (cache, refetch, mutations)
- **Zustand** for client-only state (auth, theme, sidebar)
- **react-hook-form** + **zod** for forms
- **axios** for the HTTP client (interceptors for JWT + 401 refresh)
- **recharts** for charts (dashboard KPIs)
- **date-fns** for date formatting
- **lucide-react** for icons

## Folder layout
```
frontend/
├── src/
│   ├── api/            # axios client + per-resource fns
│   ├── components/
│   │   ├── ui/         # shadcn primitives (Button, Card, Input…)
│   │   └── app/        # composed app components (CallRow, KpiCard…)
│   ├── features/       # feature folders (auth, calls, knowledge, dashboard)
│   ├── hooks/
│   ├── lib/            # utils (cn, jwt, formatters)
│   ├── routes/         # page components, one per route
│   ├── store/          # zustand stores
│   └── types/          # shared TS types
├── FRONTEND-RULES.md   # this file
└── ...
```

## API conventions
- Base URL is configured per service via env (Vite `VITE_*`).
- All tenant-facing endpoints need `Authorization: Bearer <accessToken>`.
- On 401, axios interceptor tries refresh once before logging the user out.
- `businessId` lives in the JWT — decode and store it for tenant-scoped requests.

See `frontend-api-spec.md` at repo root for the full endpoint contract.

## Rules the user set explicitly
1. React-based.
2. Folder must be named `frontend/` at the repo root.
3. Login pages for businesses.
4. Home page = dashboard showing calls + summaries + details.
5. Knowledge insertion page (per business).
6. Look professional. Be creative.
7. Save these rules so future sessions know them.
