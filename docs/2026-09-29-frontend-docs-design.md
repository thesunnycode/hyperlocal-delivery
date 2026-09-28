# Frontend build guide — design

## Why

Same motivation as the backend rewrite (see
`docs/superpowers/specs/2026-09-29-docs-rewrite-design.md`): the user wants
to type the entire application themselves, from scratch, with no internet
lookups, and come out understanding *why* every piece exists. The backend
guide covers the Spring Boot API; this is its frontend counterpart, covering
the React 19 / TypeScript / TanStack Router / Tailwind v4 + shadcn app in
`frontend/`. There is no pre-existing frontend build guide to fix — only
past one-off audit reports (`docs/audits/`, `docs/ux-audit/`) and mockups
(`docs/mockups/`), which stay exactly where they are. This is new content,
not a rewrite of something broken.

## Scope

Full build guide for `frontend/`, assuming the backend guide's API is
already built and running — frontend docs treat the backend's endpoints,
request/response shapes, and auth flow as a given contract (verified
against `docs/API-REFERENCE.md` and, once it exists, the backend guide
itself), not something to re-derive. Not in scope: rewriting the backend
guide (separate, already-planned effort), rewriting `docs/concepts/*.md`
(only touched if a new frontend task needs a concept file that doesn't
exist), rewriting past audit reports.

## 1. Phases

Ten phases, new folders `docs/phase-frontend-00-*` through
`docs/phase-frontend-09-*` (a distinct `phase-frontend-NN` numbering space,
so they never collide with the backend's `phase-00`...`phase-11`), in this
order:

0. **Setup** — Vite + React 19 + TypeScript project init, tooling
1. **UI Kit** — Tailwind v4 + shadcn/Radix primitives. Every one of the
   ~30 primitive components gets individually explained (not bulk-glossed
   over as boilerplate), grouped into task-sized clusters by purpose (e.g.
   form inputs, overlays/dialogs, layout, feedback/data-display) rather than
   one task per component.
2. **Routing & App Shell** — TanStack Router, `__root.tsx`, layout
3. **API Client & Data Layer** — `apiClient`, `types.ts`, `statusMachine`,
   TanStack Query wiring, error handling (`apiErrors.ts`)
4. **Auth Screens** — login, register, forgot-password, agent-setup
5. **Owner Screens** — agents, shipments, reports (overview/agents/trend),
   account
6. **Agent Screens** — assignments, shipment detail, account
7. **Public Tracking** — the token-based tracking page
8. **Landing Page** — marketing/public entry
9. **Production Build** — build config, env vars, SPA-fallback tie-in to
   the backend

Real folders this maps onto (verify against these when writing, not this
list): `frontend/src/components/{auth,landing,owner,rider,tracking,ui}`,
`frontend/src/routes/**`, `frontend/src/lib/hl/*`, `frontend/src/hooks/*`,
`frontend/vite.config.ts`, `frontend/package.json`.

## 2. Task granularity

Same target philosophy as the backend guide — 6-8 tasks per phase as a
target, not a hard rule — except Phase 1 (UI Kit), which is expected to run
larger given ~30 primitives are individually covered; cluster them into
task-sized groups (e.g. "form inputs: input/textarea/checkbox/radio/select,"
"overlays: dialog/popover/tooltip/dropdown-menu") rather than forcing them
into 6-8 tasks artificially or writing 30 one-component tasks. Every other
phase follows the same 6-8 target as the backend guide.

## 3. Task structure — `docs/DOC-STANDARD-FRONTEND.md`

Frontend tasks reuse the backend's 7 blocks (Header, Concept primer, Build
it narrated, Full finished code, Verify, If it breaks, Recap) plus one
added block specific to UI work:

**8. Visual reference** — what the screen/component should actually look
like once built: a description of its visual states, and a reference to
the matching file in `docs/mockups/` when one exists for that screen.
"Verify" for frontend tasks means running the dev server and confirming the
screen renders/behaves as described (not just a terminal command with text
output, as backend tasks use) — call this out explicitly in the standard so
task writers don't just copy the backend's curl-command style of Verify
block.

This needs its own file, `docs/DOC-STANDARD-FRONTEND.md`, rather than
amending the backend's `docs/DOC-STANDARD-BACKEND.md`, since the two structures
genuinely differ (8 blocks vs. 7, different Verify semantics) and forcing
one file to describe both would blur which rules apply to which guide.

## Process

Identical discipline to the backend rewrite: every task is written by
reading the real, current frontend source fresh, right now — never
invented, never assumed from typical React/shadcn conventions. "Verify"
steps must actually be run (dev server started, screen checked) before the
task is considered complete, not just described. No `DRIFT-LEDGER.md`-style
entries during this initial write (same reasoning as the backend plan —
nothing has drifted yet from content that doesn't exist).

## Out of scope

- Backend guide content or its execution status.
- `docs/concepts/*.md` rewrites (only additions if a genuinely new frontend
  concept needs one).
- Deployment/infra beyond what `frontend/vite.config.ts` and the production
  build phase already covers.
