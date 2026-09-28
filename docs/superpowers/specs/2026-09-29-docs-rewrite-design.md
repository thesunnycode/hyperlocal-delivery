# Backend build guide — full rewrite design

## Why

The current `docs/` build guide (12 phases, 121 tasks, rewritten 2026-08-25,
content-audited phase-by-phase through 2026-09-29) is content-accurate — every
phase was just freshly re-verified against real source in this session. But
accuracy isn't the goal anymore. The user's actual goal: use these docs as the
**sole source of truth** to rebuild the entire application themselves, line by
line, with no internet lookups, and come out the other side actually
understanding *why* every piece exists and how it works — not just what to
type. The current guide is fragmented into too many small, single-class task
files (up to 19 tasks in one phase) and explains reasoning ("why these
lines") only *after* showing finished code, as a retrospective rather than
as understanding built up front. Both of those work against the learning
goal, so this is a structural rewrite, not another content patch.

## Scope

Full rewrite of the build-guide task content, phase by phase, in the same
12-phase dependency order as the current guide (00-setup through
11-agent-invites) — the *order* isn't changing, only how each phase's tasks
are chunked and how each task teaches. Not in scope: the frontend (still
out of scope per the original `REWRITE-PLAN.md` decision), and
`docs/concepts/*.md` content itself (structure/location unchanged, may get
light touch-ups if a rewritten task needs a concept file to cover something
new, but this isn't a concepts rewrite).

## 1. Archival

Move the entire current build guide and its supporting audit apparatus into
`docs/old-docs/`, preserving internal structure exactly as-is (a pure
`git mv`-style relocation, no content changes):

- `docs/phase-00-setup/` through `docs/phase-11-agent-invites/` (all 12 folders)
- `docs/DOC-STANDARD.md`
- `docs/REWRITE-PLAN.md`
- `docs/AUDIT-PROMPT.md`
- `docs/AUDIT-REPORT.md`
- `docs/DRIFT-LEDGER.md`
- `docs/STRUCTURAL-PASS-PROMPT.md`
- `docs/_audit_scan.ps1`
- `docs/FLOW-AUDIT-PROMPT.md`
- `docs/FRONTEND-SCREEN-AUDIT-PROMPT.md`
- `docs/ui-ux-audit-prompt.md`
- `docs/audits/`
- `docs/ux-audit/`
- `docs/mockups/`

**Not archived, stays live at its current path:**

- `docs/concepts/` — the reference layer the new guide keeps linking out to.
- `docs/ARCHITECTURE.md`, `docs/API-REFERENCE.md` — these describe the real,
  current, finished system rather than being build-guide artifacts; they're
  accurate (verified elsewhere this session) and useful as ground truth while
  writing new task content.

After the move, `CLAUDE.md`'s documentation-set table needs updating to
describe the new (empty, then filling-in) `docs/phase-*` structure and to
mention `docs/old-docs/` as the frozen prior attempt (read-only reference,
not maintained going forward — no more `DRIFT-LEDGER.md`-style upkeep on it).

## 2. New phase/task granularity

Same 12 phases, same dependency order. Target **roughly 6-8 tasks per
phase**, each task building one cohesive, independently-compilable/runnable
domain slice — grouped by *feature area*, not by individual class. Examples
of the collapse this implies (exact split decided per-phase when that phase
is actually written, not locked in advance):

- Phase 03 (JPA Entities & Repos): 19 tasks → ~5-6, e.g. "Business + User
  entities and repos," "Shipment + ShipmentEvent entities and repos,"
  "Auth-support entities: RefreshToken/OtpRecord/PendingRegistration."
- Phase 05 (Auth & OTP): 15 tasks → ~6-7, grouped by flow (registration, OTP
  verify, login, password reset) instead of one file per DTO/service method.

Every other phase gets the same treatment: fewer, larger, flow-grouped tasks
instead of one-file-per-class. No fixed multiplier — each phase's real task
list is designed when that phase is written, using this range as a target,
not a hard rule.

## 3. New task file structure (replaces the old 8-block format)

Old format explained "why these lines" *after* the finished-code block —
reasoning as an afterthought. New format puts concept before/alongside code:

1. **Header** — what this task builds, why it matters in the overall system,
   files touched, prerequisites.
2. **Concept primer** — the specific ideas needed *before* writing this
   task's code (e.g., before the JWT filter: what a filter chain is, why
   signing vs. encryption, why validation happens per-request). Short;
   links to the relevant `docs/concepts/*.md` file for the deep version.
3. **Build it, narrated** — numbered steps; each step's code is immediately
   followed by why that line/class/annotation is there, inline, not deferred.
4. **Full finished code** — the complete files as a clean reference to
   check a typed-out version against.
5. **Verify** — command, expected output, and what the output actually
   proves about the system (not just "run this and see this").
6. **If it breaks** — mistakes specific to *this* task, not a generic list.
7. **Recap** — one paragraph: what you now understand that you didn't
   before this task, and what the next task builds on top of it.

Seven blocks instead of eight (folding "why these lines" into the narrated
build steps). Line-count targets are **not** fixed in advance — they get set
per-phase from real written output once phase content exists, since
inventing a bracket before writing a single chunked-up task is guesswork.
`docs/DOC-STANDARD.md` gets rewritten to describe this new structure,
replacing (not amending) the old 8-block spec.

## Process for writing the new guide

- Work phase by phase, in order, starting from `docs/phase-00-setup/`.
- Every task is written by reading the **real current source** fresh (same
  discipline as this session's audit passes) — never copied forward from
  `docs/old-docs/`'s content, since the whole point is a from-scratch,
  verified-against-reality rewrite, not a reformat of possibly-stale prose.
  `docs/old-docs/` may be consulted for *scope reminders* (what topics a
  phase used to cover) but never as a source of technical claims.
- No `DRIFT-LEDGER.md`-style ongoing audit trail for the new guide during
  this rewrite — that mechanism was for maintaining an already-written guide
  against a moving codebase; while actively writing fresh content there's
  nothing to have drifted yet. (Whether to reinstate that discipline once the
  new guide is complete is a later decision, out of scope here.)

## Out of scope

- Rewriting `docs/concepts/*.md` (only touched if a new task needs a concept
  file that doesn't exist yet).
- Any change to the actual application code.
- Frontend documentation (still out of scope per the original rewrite's
  decision).
