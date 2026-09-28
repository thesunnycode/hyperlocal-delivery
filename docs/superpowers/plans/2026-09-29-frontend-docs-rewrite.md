# Frontend Build Guide Rewrite — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write a from-scratch, no-internet frontend build guide (`docs/phase-frontend-00-setup` through `docs/phase-frontend-09-production-build`) so a reader can type the entire React/TypeScript frontend themselves and understand why every piece exists, assuming the backend guide's API is already built and running.

**Architecture:** No code changes. Documentation project against an already-complete, already-verified frontend (`frontend/src/**`). Each phase's tasks are written by reading the real, current source fresh, describing how to build that already-existing code from an empty Vite project.

**Tech Stack:** Markdown docs only. Reference material: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`, `frontend/components.json`, everything under `frontend/src/**`, `docs/API-REFERENCE.md`, `docs/ARCHITECTURE.md`, `docs/DOC-STANDARD-FRONTEND.md`.

## Global Constraints

- **8-block task structure, every task file, no exceptions** — per `docs/DOC-STANDARD-FRONTEND.md`: Header, Concept primer, Build it narrated, Full finished code, Verify, Visual reference, If it breaks, Recap.
- **Never invent from typical React/shadcn conventions.** Every technical claim comes from reading the real, current file in `frontend/src/**` fresh, right now.
- **Verify steps must actually be run** — start the dev server (`cd frontend && npm run dev`), visit the real route, confirm the described behavior. Don't just describe a command; run it.
- **Target 6-8 tasks per phase**, except Phase 1 (UI Kit), which covers **46 real primitive files** (not ~30 as originally estimated in the design spec — corrected here after listing the real `frontend/src/components/ui/` folder) and is expected to run larger; cluster them into task-sized groups by purpose.
- **Real component structure note (corrects an assumption in the design spec):** the app-specific screens are NOT ~24 small components — they're 5 large "Live" files, each holding multiple inline sub-components for a whole role/area: `components/auth/AuthLive.tsx` (118 lines), `components/owner/OwnerLive.tsx` (333 lines), `components/rider/RiderLive.tsx` (141 lines), `components/tracking/TrackingLive.tsx` (98 lines), `components/landing/LandingPage.tsx` (157 lines). Phase tasks for auth/owner/agent/tracking screens must be written against these real files, breaking a single "Live" file's inline sub-components into separate tasks where they're independently understandable, not assuming a one-file-per-screen structure that doesn't exist.
- **No `DRIFT-LEDGER.md`-style entries during this initial write.**
- Backend-only contracts are given, not re-derived — cite `docs/API-REFERENCE.md` for endpoint shapes, but verify against the real backend source if anything looks inconsistent.

---

## File Structure

**Created by this plan:**
- `docs/phase-frontend-00-setup/` … `docs/phase-frontend-09-production-build/` (10 new folders, each with `00-task-breakdown.md` + `task-NN-*.md` files)

**Already exists, consumed not modified:**
- `docs/DOC-STANDARD-FRONTEND.md` (the 8-block format contract)
- `docs/superpowers/specs/2026-09-29-frontend-docs-design.md` (design rationale)

---

### Task 1: Write `docs/phase-frontend-00-setup/`

**Files:**
- Create: `docs/phase-frontend-00-setup/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: `docs/DOC-STANDARD-FRONTEND.md` (8-block structure, sizing target). Assumes the backend guide's environment setup (phase-00) is already done — JDK/Maven/MySQL not repeated here.
- Produces: a running empty Vite + React 19 + TypeScript project that phase-frontend-01 (UI kit) installs into.

- [ ] **Step 1: Read real setup files fresh**

Read, in full: `frontend/package.json`, `frontend/vite.config.ts`,
`frontend/tsconfig.json`, `frontend/eslint.config.js`, `frontend/index.html`,
`frontend/.gitignore`. Run `node --version` and `npm --version` on this
machine (do not assume — the backend `CLAUDE.md` notes Node is fetched by
`frontend-maven-plugin` at `v20.19.0`; confirm what's actually available
when working in `frontend/` standalone).

- [ ] **Step 2: Draft the task breakdown**

Target 6-7 tasks: create Vite+React+TS project, `vite-tsconfig-paths` +
path aliases, ESLint + Prettier config, `vitest` + Testing Library setup,
`components.json` (shadcn CLI config) primer, first run + commit.

- [ ] **Step 3: Write each task file**

Follow the 8-block structure. Every version number (React 19.2.0, Vite
7.1.0, TypeScript 5.8.3) must be copied from the real `package.json` read
in Step 1, not from memory of "current" versions.

- [ ] **Step 4: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend"
npm run dev
```

Confirm the dev server starts (visit `http://localhost:5173`) — describe
in the Verify block what an empty/fresh project's default page looks like
at this point in the guide (before real screens exist).

- [ ] **Step 5: Commit**

```bash
git add docs/phase-frontend-00-setup
git commit -m "docs: write frontend phase 00 (setup) from scratch, 8-block format"
```

---

### Task 2: Write `docs/phase-frontend-01-ui-kit/`

**Files:**
- Create: `docs/phase-frontend-01-ui-kit/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 00 complete.
- Produces: the full Tailwind v4 + Radix/shadcn primitive set (46 files)
  that every later screen phase imports from `components/ui/`.

- [ ] **Step 1: Read every real primitive file fresh**

Read, in full, all 46 files in `frontend/src/components/ui/` (listed via
`ls frontend/src/components/ui`), plus `frontend/components.json` (shadcn
config) and the Tailwind setup in `frontend/src/styles.css` and
`frontend/vite.config.ts`'s `@tailwindcss/vite` plugin entry.

- [ ] **Step 2: Cluster into task-sized groups**

Do not write 46 one-file tasks or force them into 6-8. Group by purpose,
e.g.: Tailwind v4 + `tw-animate-css` setup (no component yet), form inputs
(`input`, `textarea`, `checkbox`, `radio-group`, `select`, `switch`,
`slider`, `form`, `label`), overlays (`dialog`, `alert-dialog`, `sheet`,
`drawer`, `popover`, `hover-card`, `tooltip`, `dropdown-menu`,
`context-menu`, `menubar`), navigation/layout (`tabs`, `accordion`,
`collapsible`, `breadcrumb`, `navigation-menu`, `sidebar`, `separator`,
`aspect-ratio`, `resizable`, `scroll-area`), feedback/data-display
(`alert`, `badge`, `card`, `progress`, `skeleton`, `sonner`, `table`,
`pagination`, `chart`, `calendar`, `carousel`), misc/input-adjacent
(`avatar`, `button`, `command`, `input-otp`, `toggle`, `toggle-group`).
Adjust grouping once you've actually read the files — this is a starting
point, not a fixed split.

- [ ] **Step 3: Write each task file**

Every primitive gets individual explanation (why this Radix primitive, what
composition pattern it uses, e.g. `Slot`/`asChild`) inside its cluster's
task — per the design spec's explicit choice to not bulk-gloss the UI kit.

- [ ] **Step 4: Verify**

For at least 3 primitives across different clusters, confirm the finished
code block matches the real file exactly:

```bash
diff <(sed -n '/```tsx/,/```/p' docs/phase-frontend-01-ui-kit/task-0X-*.md | sed '1d;$d') frontend/src/components/ui/button.tsx
```

(Repeat for a form-input primitive and an overlay primitive.)

- [ ] **Step 5: Commit**

```bash
git add docs/phase-frontend-01-ui-kit
git commit -m "docs: write frontend phase 01 (UI kit) from scratch, 8-block format"
```

---

### Task 3: Write `docs/phase-frontend-02-routing-and-shell/`

**Files:**
- Create: `docs/phase-frontend-02-routing-and-shell/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 01 complete.
- Produces: the TanStack Router setup and root layout every screen phase
  mounts its routes onto.

- [ ] **Step 1: Read every real routing file fresh**

Read, in full: `frontend/src/router.tsx`, `frontend/src/routes/__root.tsx`,
`frontend/src/routeTree.gen.ts` (generated — read to understand what it
contains, note in the task that it's generated not hand-written),
`frontend/src/main.tsx`, and list every file under `frontend/src/routes/`
(`find frontend/src/routes -type f`) to confirm the real route tree shape
(including nested `owner/reports/*` and `agent/shipments/$id.tsx`).

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6 tasks: TanStack Router install + file-based routing concept,
`router.tsx` + code-gen plugin, `__root.tsx` (root layout, providers),
route file naming conventions (`$id` params, nested folders), a minimal
placeholder route to prove the wiring, verify + commit.

- [ ] **Step 3: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend" && npm run dev
```

Visit a real nested route (e.g. `/owner/reports/overview`) and confirm it
resolves — describe the exact URL structure in the Verify block.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-frontend-02-routing-and-shell
git commit -m "docs: write frontend phase 02 (routing and shell) from scratch, 8-block format"
```

---

### Task 4: Write `docs/phase-frontend-03-api-client/`

**Files:**
- Create: `docs/phase-frontend-03-api-client/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 02 complete. Assumes the backend guide's API is running
  (real endpoint shapes from `docs/API-REFERENCE.md`).
- Produces: the typed API client every screen phase (04-07) calls into.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `frontend/src/lib/hl/apiClient.ts` (231 lines),
`apiErrors.ts` (108 lines), `types.ts` (509 lines), `statusMachine.ts` (114
lines) + `statusMachine.test.ts` (30 lines), `authApi.ts` (133 lines),
`agentsApi.ts` (38 lines), `shipmentsApi.ts` (133 lines), `reportsApi.ts`
(89 lines), `format.ts` (156 lines).

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 7-8 tasks: `apiClient` core (fetch wrapper, base URL, auth header
injection), `apiErrors` (error shape mapping to the real backend
`ApiError` envelope), `types.ts` (the TypeScript mirror of backend DTOs —
cross-check a sample against the real backend DTO, e.g. confirm
`expiresAt: string` not `Date`, matching the backend's `TimeUtils.toIso`
convention), `statusMachine` (+ its own real Vitest test file as the
Verify step for that task), TanStack Query wiring/hooks, the 4 domain API
modules (`authApi`/`agentsApi`/`shipmentsApi`/`reportsApi`), `format.ts`
helpers, verify + commit.

- [ ] **Step 3: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend" && npx vitest run src/lib/hl/statusMachine.test.ts
```

Confirm the real test suite passes, and that the task describing
`statusMachine.ts` matches what the test actually exercises.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-frontend-03-api-client
git commit -m "docs: write frontend phase 03 (API client) from scratch, 8-block format"
```

---

### Task 5: Write `docs/phase-frontend-04-auth-screens/`

**Files:**
- Create: `docs/phase-frontend-04-auth-screens/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 03 complete.
- Produces: login/register/forgot-password/agent-setup screens that
  phase-frontend-05/06 assume a logged-in session for.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `frontend/src/components/auth/AuthLive.tsx` (118 lines —
read all of it; per the Global Constraints note, this single file likely
holds multiple inline sub-components including `LiveSetup` and possibly a
forgot-password view), `frontend/src/routes/login.tsx`,
`frontend/src/routes/register.tsx`, `frontend/src/routes/forgot-password.tsx`,
`frontend/src/routes/agent-setup.tsx`, `frontend/src/routes/owner/register.tsx`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6-7 tasks, one per distinct inline component/flow found in
`AuthLive.tsx` in Step 1 (do not guess the split before reading — name the
actual sub-components found): login form + route wiring, register flow
(+ OTP step if present), forgot-password flow, agent-setup
(`LiveSetup`, per the earlier session's audit finding), form
validation via `react-hook-form` + `zod`, verify + commit.

- [ ] **Step 3: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend" && npm run dev
```

Visit `/login`, `/register`, `/forgot-password`, `/agent-setup` against a
running backend; describe each screen's actual states (empty, submitting,
error, success) in the Visual reference block, checking against
`docs/mockups/02-auth.html` through `07-auth-v6.html` if one matches.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-frontend-04-auth-screens
git commit -m "docs: write frontend phase 04 (auth screens) from scratch, 8-block format"
```

---

### Task 6: Write `docs/phase-frontend-05-owner-screens/`

**Files:**
- Create: `docs/phase-frontend-05-owner-screens/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 04 complete.
- Produces: the owner-role screens.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `frontend/src/components/owner/OwnerLive.tsx` (333 lines —
the largest single file; per the earlier session's audit it contains at
least an inline `InviteSheet` component, likely others — read all of it to
find every inline sub-component), `frontend/src/routes/owner/agents.tsx`,
`frontend/src/routes/owner/shipments.tsx`, `frontend/src/routes/owner/account.tsx`,
`frontend/src/routes/owner/reports/overview.tsx`,
`frontend/src/routes/owner/reports/agents.tsx`,
`frontend/src/routes/owner/reports/trend.tsx`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 7-8 tasks, named after the real inline components/screens found in
Step 1 (do not pre-guess the exact split): agent roster (list/create/
deactivate + `InviteSheet`), shipment list + create, shipment
detail/reassign, account settings, reports overview, reports (agent perf +
trend), verify + commit. Cross-check each screen's data fetching against
the real API client from phase-frontend-03.

- [ ] **Step 3: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend" && npm run dev
```

Visit each real owner route against a running backend; use
`docs/mockups/10-owner-console.html`, `11-owner-states.html`,
`12-reports.html` and `docs/ux-audit/screens/` (real captured screenshots,
e.g. `d-owner-agents.png`, `d-owner-shipments.png`) as Visual reference
anchors where they match.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-frontend-05-owner-screens
git commit -m "docs: write frontend phase 05 (owner screens) from scratch, 8-block format"
```

---

### Task 7: Write `docs/phase-frontend-06-agent-screens/`

**Files:**
- Create: `docs/phase-frontend-06-agent-screens/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 04 complete (doesn't strictly need Phase 05).
- Produces: the agent/rider-role screens.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `frontend/src/components/rider/RiderLive.tsx` (141 lines),
`frontend/src/routes/agent/assignments.tsx`,
`frontend/src/routes/agent/account.tsx`,
`frontend/src/routes/agent/shipments/$id.tsx`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6 tasks: assignments list (today's deliveries), shipment detail +
status-advance actions (tying into `statusMachine` from phase-frontend-03),
fail-delivery flow (matching the real backend `FailureReason` enum from
`lib/hl/types.ts`), account settings, verify + commit.

- [ ] **Step 3: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend" && npm run dev
```

Visit agent routes against a running backend with a real agent login; use
`docs/mockups/08-agent-app.html` and `docs/ux-audit/screens/m-agent-*.png`
as Visual reference anchors.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-frontend-06-agent-screens
git commit -m "docs: write frontend phase 06 (agent screens) from scratch, 8-block format"
```

---

### Task 8: Write `docs/phase-frontend-07-public-tracking/`

**Files:**
- Create: `docs/phase-frontend-07-public-tracking/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 03 complete (doesn't need auth screens — this is a
  public, unauthenticated route).
- Produces: the public tracking page.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `frontend/src/components/tracking/TrackingLive.tsx` (98
lines), `frontend/src/routes/track/$token.tsx`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 5-6 tasks: route + token param handling, public API call (no auth
header — confirm this against `apiClient.ts` from phase-frontend-03), status
display states (in-transit/delivered/failed/cancelled/not-found), verify +
commit.

- [ ] **Step 3: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend" && npm run dev
```

Visit `/track/<real-token>` against a running backend for shipments in
different statuses; use `docs/mockups/01-customer-tracking.html`,
`09-customer-tracking.html` and `docs/ux-audit/screens/m-track-*.png`/
`d-track-*.png` as Visual reference anchors for each status.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-frontend-07-public-tracking
git commit -m "docs: write frontend phase 07 (public tracking) from scratch, 8-block format"
```

---

### Task 9: Write `docs/phase-frontend-08-landing-page/`

**Files:**
- Create: `docs/phase-frontend-08-landing-page/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 02 complete (routing/shell) — this phase can be built any
  time after routing exists; doesn't depend on auth/owner/agent phases.
- Produces: the marketing/public entry page.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `frontend/src/components/landing/LandingPage.tsx` (157
lines), `frontend/src/routes/index.tsx`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 4-5 tasks: route wiring, page sections found in the real file (read
first, name them accurately rather than guessing "hero/features/footer"),
verify + commit.

- [ ] **Step 3: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend" && npm run dev
```

Visit `/` and confirm it renders as described.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-frontend-08-landing-page
git commit -m "docs: write frontend phase 08 (landing page) from scratch, 8-block format"
```

---

### Task 10: Write `docs/phase-frontend-09-production-build/`

**Files:**
- Create: `docs/phase-frontend-09-production-build/00-task-breakdown.md`, `task-01-*.md` … `task-0N-*.md`

**Interfaces:**
- Consumes: all prior frontend phases complete.
- Produces: the final phase — production build config and the tie-in to
  the backend's SPA fallback (phase-10 of the backend guide).

- [ ] **Step 1: Read every real file fresh**

Read, in full: `frontend/vite.config.ts` (build output path — confirm it
matches where the backend's `frontend-maven-plugin`/`SpaFallbackController`
expects static assets, per `docs/ARCHITECTURE.md` and the backend's
`WebConfig.java`/`SpaFallbackController.java` if cross-referencing),
`frontend/package.json`'s `build`/`build:dev` scripts, root `pom.xml`'s
`frontend-maven-plugin` block (confirm the real Node version pin and build
command it invokes).

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 5-6 tasks: production build command + output verification, how the
backend serves the built assets (cross-reference, don't re-document the
backend side), environment-specific API base URL handling, final full
end-to-end verify (both servers running together) + commit.

- [ ] **Step 3: Verify**

```bash
cd "/d/temporary resume/Hyperlocal/frontend" && npm run build
```

Confirm the build succeeds and inspect the real output directory it
produces.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-frontend-09-production-build
git commit -m "docs: write frontend phase 09 (production build) from scratch, 8-block format"
```

---

## Final check (after Task 10)

- [ ] Confirm `docs/phase-frontend-00-setup` through
  `docs/phase-frontend-09-production-build` all exist, each with a
  `00-task-breakdown.md`.
- [ ] Run `find docs -maxdepth 1 -name "phase-frontend-*" | sort` and
  confirm all 10 phases are present in order.
- [ ] Spot-check 3 phases at random by re-running one Verify command from
  that phase's task above (dev server + visit the real route), to confirm
  nothing drifted between writing and this final pass.
