# Doc standard — frontend build guide (2026-09-29)

Companion to `docs/DOC-STANDARD.md` (the backend guide's format). This file
governs every `task-NN-*.md` file under `docs/phase-frontend-*/`. The two
guides share the same teaching philosophy (concept before code, reasoning
inline, read-real-source discipline) but use different block structures —
do not use the backend's 7-block format for a frontend task, and do not use
this file's 8-block format for a backend task.

## Why an 8th block

Backend tasks verify with a terminal command and a text/JSON output — that
fully captures correctness. Frontend tasks build something visual; a
passing `tsc`/build step doesn't tell you the screen looks or behaves
right. This guide adds a dedicated block for what the reader should
actually see, so "verify" isn't just "it compiled."

## The 8 blocks, in order

### 1. Header
- Task title, one-sentence what-this-builds
- Why it matters in the overall app (2-3 sentences)
- Files touched (exact paths, created vs. modified)
- Prerequisites (which earlier tasks/phases this depends on — including,
  where relevant, which backend endpoint(s) this screen/component consumes)

### 2. Concept primer
- The specific ideas needed before writing this task's code (e.g. before a
  TanStack Query mutation: what a mutation is, why optimistic updates
  matter here, why this differs from a plain `fetch`)
- Link to the relevant `docs/concepts/*.md` file(s) for the deep version,
  if one exists — most frontend-specific concepts (React Query, file-based
  routing, Radix primitives) won't have one yet; note in the task if a new
  concept file is warranted rather than inventing an inline mini-lecture
  every time
- Short — a few paragraphs, not a tutorial

### 3. Build it, narrated
- Numbered steps
- Each step's code immediately followed by why that line/prop/hook call is
  there — inline, not deferred
- Every claim about the code must match the real, current source file in
  `frontend/src/**` exactly, verified fresh when the task is written

### 4. Full finished code
- The complete file(s) this task produces, as a clean reference block
- Must be byte-for-byte identical to the real, current source

### 5. Verify
- The exact command to run (usually `npm run dev` from `frontend/`, or a
  build/lint/typecheck command)
- What to actually do in the browser once it's running (which route to
  visit, which interaction to try)
- What you should see, described concretely (not "it should look right" —
  specific states: loading, populated, error, empty)
- What that behavior actually proves about the code just written

### 6. Visual reference
- A concrete description of the screen/component's visual states
  (default, hover/focus where relevant, loading, error, empty)
- A reference to the matching file under `docs/mockups/` when one exists
  for this screen — name it exactly, don't paraphrase which mockup applies
- If no mockup exists for this piece, say so explicitly rather than
  silently omitting the block

### 7. If it breaks
- Mistakes specific to THIS task (wrong import path, missing `"use
  client"`-equivalent concern, wrong Tailwind class, prop name typo) — not
  a generic React/Vite troubleshooting list

### 8. Recap
- One paragraph: what you now understand that you didn't before this task
- One sentence: what the next task builds on top of it

## Task sizing

Target 6-8 tasks per phase, same as the backend guide, with one exception:
**Phase 1 (UI Kit)** covers ~30 shadcn/Radix primitive components,
individually explained — cluster them into task-sized groups by purpose
(e.g. "form inputs," "overlays/dialogs," "layout," "feedback/data-display")
rather than forcing 30 components into 6-8 tasks or writing one task per
component. No fixed line-count bracket; each task is as long as its
content genuinely needs.

## Phase folder layout

Each `docs/phase-frontend-NN-name/` folder contains:
- `00-task-breakdown.md` — one-line summary of every task in the phase, in
  order, with estimated time and prerequisites
- `task-01-*.md` through `task-NN-*.md` — one file per task, following the
  8-block structure above

## Phase list

0. `phase-frontend-00-setup` — Vite + React 19 + TypeScript project init
1. `phase-frontend-01-ui-kit` — Tailwind v4 + shadcn/Radix primitives
2. `phase-frontend-02-routing-and-shell` — TanStack Router, `__root.tsx`
3. `phase-frontend-03-api-client` — `apiClient`, `types.ts`,
   `statusMachine`, TanStack Query, `apiErrors.ts`
4. `phase-frontend-04-auth-screens` — login, register, forgot-password,
   agent-setup
5. `phase-frontend-05-owner-screens` — agents, shipments, reports, account
6. `phase-frontend-06-agent-screens` — assignments, shipment detail,
   account
7. `phase-frontend-07-public-tracking` — token-based tracking page
8. `phase-frontend-08-landing-page` — marketing/public entry
9. `phase-frontend-09-production-build` — build config, env vars, SPA
   fallback tie-in to the backend
