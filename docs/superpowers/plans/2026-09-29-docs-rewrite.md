# Backend Build Guide Rewrite — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the entire `docs/` backend build guide with a from-scratch rewrite — archiving the current 12-phase/121-task guide, and rewriting it with coarser task granularity (6-8 tasks/phase, grouped by feature slice) and a concept-before-code task structure, so a reader with no internet access can type the guide start to finish and understand *why* every piece exists, not just what to type.

**Architecture:** No code changes. This is a documentation project against an already-complete, already-verified-accurate application (`src/main/java/com/hyperlocal/delivery/...`, `src/main/resources/db/migration/V1-V10`). Each phase's tasks are written by reading the real, current source fresh — never copied forward from the archived guide — and describe how to build that already-existing code from an empty project, in the same way the old guide did, but chunked and taught differently.

**Tech Stack:** Markdown docs only. Reference material: `pom.xml`, `src/main/java/com/hyperlocal/delivery/**`, `src/main/resources/db/migration/V*.sql`, `src/main/resources/application*.yml`, `docs/concepts/*.md`, `docs/ARCHITECTURE.md`, `docs/API-REFERENCE.md`.

## Global Constraints

- **Never copy prose/code from `docs/old-docs/`.** Every technical claim in the new guide must come from reading the real, current source file fresh, right now — `docs/old-docs/` may only be consulted to remind yourself what topics a phase used to cover (scope reminder), never as a source of facts.
- **7-block task structure, every task file, no exceptions:** (1) Header, (2) Concept primer, (3) Build it, narrated (reasoning inline with each step, not deferred), (4) Full finished code, (5) Verify, (6) If it breaks, (7) Recap. This replaces the old 8-block format — do not reuse it.
- **Target 6-8 tasks per phase**, each building one cohesive, independently-compilable/runnable domain slice grouped by feature area — never one task per single class/file. This is a target, not a hard rule; a phase can be 5 or 9 if the natural feature slices demand it, but if you land outside 5-9 stop and reconsider the grouping.
- **No fixed line-count bracket.** Each phase task sets its own reasonable length once written; do not invent or reuse the old guide's brackets.
- **No `DRIFT-LEDGER.md`-style entries for this rewrite.** That mechanism is for maintaining an already-written guide against a moving codebase; skip it while writing fresh content.
- Backend-only scope — do not document the frontend beyond what the backend serves/expects (same boundary the old guide used).
- Same 12-phase, dependency-ordered sequence as the old guide (00-setup → 11-agent-invites) — the order is not changing, only the internal chunking/teaching style.

---

## File Structure

**Moved (archived), not recreated by this plan — see Task 1:**
- `docs/old-docs/phase-00-setup/` … `docs/old-docs/phase-11-agent-invites/`
- `docs/old-docs/DOC-STANDARD-BACKEND.md`, `REWRITE-PLAN.md`, `AUDIT-PROMPT.md`, `AUDIT-REPORT.md`, `DRIFT-LEDGER.md`, `STRUCTURAL-PASS-PROMPT.md`, `_audit_scan.ps1`, `FLOW-AUDIT-PROMPT.md`, `FRONTEND-SCREEN-AUDIT-PROMPT.md`, `ui-ux-audit-prompt.md`, `audits/`, `ux-audit/`, `mockups/`

**Stays live, untouched by this plan:**
- `docs/concepts/*.md`, `docs/ARCHITECTURE.md`, `docs/API-REFERENCE.md`

**Created by this plan:**
- `docs/DOC-STANDARD-BACKEND.md` (new, replaces the archived one — describes the 7-block structure)
- `docs/phase-00-setup/00-task-breakdown.md` + `task-01..NN-*.md` (new content)
- `docs/phase-01-project-init/` … `docs/phase-11-agent-invites/` (same pattern, new content, one task in this plan per phase)

**Modified:**
- `CLAUDE.md` — documentation-set table updated to describe the new guide + `docs/old-docs/` as frozen reference

---

### Task 1: Archive the current guide

**Files:**
- Move: `docs/phase-00-setup/` … `docs/phase-11-agent-invites/` → `docs/old-docs/phase-00-setup/` … `docs/old-docs/phase-11-agent-invites/`
- Move: `docs/DOC-STANDARD-BACKEND.md`, `docs/REWRITE-PLAN.md`, `docs/AUDIT-PROMPT.md`, `docs/AUDIT-REPORT.md`, `docs/DRIFT-LEDGER.md`, `docs/STRUCTURAL-PASS-PROMPT.md`, `docs/_audit_scan.ps1`, `docs/FLOW-AUDIT-PROMPT.md`, `docs/FRONTEND-SCREEN-AUDIT-PROMPT.md`, `docs/ui-ux-audit-prompt.md` → same filenames under `docs/old-docs/`
- Move: `docs/audits/`, `docs/ux-audit/`, `docs/mockups/` → `docs/old-docs/audits/`, `docs/old-docs/ux-audit/`, `docs/old-docs/mockups/`
- Modify: `CLAUDE.md` (documentation-set section)

**Interfaces:**
- Produces: `docs/old-docs/` as a frozen, read-only tree every later task may consult for scope reminders only.

- [ ] **Step 1: Move the phase folders**

```bash
cd "/d/temporary resume/Hyperlocal"
mkdir -p docs/old-docs
for d in docs/phase-*; do git mv "$d" "docs/old-docs/$(basename "$d")"; done
```

- [ ] **Step 2: Move the standalone guide/audit files**

```bash
for f in DOC-STANDARD-BACKEND.md REWRITE-PLAN.md AUDIT-PROMPT.md AUDIT-REPORT.md DRIFT-LEDGER.md STRUCTURAL-PASS-PROMPT.md _audit_scan.ps1 FLOW-AUDIT-PROMPT.md FRONTEND-SCREEN-AUDIT-PROMPT.md ui-ux-audit-prompt.md; do
  git mv "docs/$f" "docs/old-docs/$f" 2>/dev/null || mv "docs/$f" "docs/old-docs/$f"
done
```

Note: several of these files are currently untracked (`??` in `git status`), so `git mv` will fail on those — the `mv` fallback handles it. After moving, run `git add docs/old-docs` to pick up the untracked files at their new path.

- [ ] **Step 3: Move the audit-report subfolders**

```bash
for d in audits ux-audit mockups; do
  git mv "docs/$d" "docs/old-docs/$d" 2>/dev/null || mv "docs/$d" "docs/old-docs/$d"
done
git add docs/old-docs
```

- [ ] **Step 4: Verify the move**

```bash
ls docs/
```

Expected: only `old-docs/`, `concepts/`, `ARCHITECTURE.md`, `API-REFERENCE.md`, `superpowers/` (this plan/spec's own folder) remain outside `old-docs/` — no `phase-*` folders, no `DOC-STANDARD-BACKEND.md`, at the top level of `docs/`.

- [ ] **Step 5: Update CLAUDE.md's documentation-set table**

Replace the current table (the one listing `REWRITE-PLAN.md`, `DOC-STANDARD-BACKEND.md`, `AUDIT-PROMPT.md`, etc.) with:

```markdown
`docs/` holds the backend build guide, rewritten from scratch on 2026-09-29
with coarser, feature-sliced tasks and a concept-before-code structure (see
`docs/DOC-STANDARD-BACKEND.md`). Twelve phase folders, `phase-00-setup` through
`phase-11-agent-invites`, each with a `00-task-breakdown.md` and per-task
`task-NN-*.md` files, plus `concepts/`.

| File | What it is |
|---|---|
| `docs/DOC-STANDARD-BACKEND.md` | The locked format every task file follows (the 7-block task structure) |
| `docs/ARCHITECTURE.md`, `docs/API-REFERENCE.md` | System diagrams and endpoint reference for the real, current app |
| `docs/old-docs/` | The frozen, no-longer-maintained prior build guide and its audit apparatus (`DRIFT-LEDGER.md`, `AUDIT-PROMPT.md`, etc.) — read-only reference for what topics used to be covered, never a source of technical claims for the current guide |

### Rules when working on the docs

- Every technical claim comes from reading the real, current source fresh —
  never from `docs/old-docs/`, never from memory.
- Follow `docs/DOC-STANDARD-BACKEND.md`'s 7-block structure for every task file.
```

Keep the rest of `CLAUDE.md` (build/run instructions, environment facts,
repository conventions) unchanged — only the documentation-set section
changes.

- [ ] **Step 6: Commit**

```bash
git add -A docs CLAUDE.md
git commit -m "docs: archive prior build guide to docs/old-docs/, update CLAUDE.md index"
```

---

### Task 2: Write the new `docs/DOC-STANDARD-BACKEND.md`

**Files:**
- Create: `docs/DOC-STANDARD-BACKEND.md`

**Interfaces:**
- Consumes: the 7-block structure and constraints from the spec (`docs/superpowers/specs/2026-09-29-docs-rewrite-design.md`).
- Produces: the format contract every phase-writing task (Tasks 3-14) must follow exactly.

- [ ] **Step 1: Write the structure section**

Create `docs/DOC-STANDARD-BACKEND.md` describing, for every task file:

```markdown
# Doc standard — backend build guide (rewritten 2026-09-29)

Every `task-NN-*.md` file in every `docs/phase-*/` folder follows this
7-block structure, in this order. Do not add, remove, reorder, or rename
blocks.

## 1. Header
- Task title, one-sentence what-this-builds
- Why it matters in the overall system (2-3 sentences)
- Files touched (exact paths, created vs. modified)
- Prerequisites (which earlier tasks/phases this depends on)

## 2. Concept primer
- The specific ideas needed before writing this task's code — only what's
  needed for THIS task, not a general tutorial
- Link to the relevant docs/concepts/*.md file(s) for the deep version
- Short: a few paragraphs, not a full lecture

## 3. Build it, narrated
- Numbered steps
- Each step: the code for that step, immediately followed by why that
  line/class/annotation/config key is there — inline, not deferred to a
  separate section
- Every claim about the code must match the real, current source file
  exactly (verified fresh when the task is written)

## 4. Full finished code
- The complete file(s) this task produces, as a clean reference block,
  for checking a typed-out version against
- Must be byte-for-byte identical to the real, current source

## 5. Verify
- The exact command to run
- The exact expected output
- What that output actually proves about the system (not just "you should
  see this")

## 6. If it breaks
- Mistakes specific to THIS task (wrong import, wrong annotation order,
  typo in a config key) — not a generic Spring Boot troubleshooting list

## 7. Recap
- One paragraph: what you now understand that you didn't before this task
- One sentence: what the next task builds on top of this

## Task sizing

Target 6-8 tasks per phase, each building one cohesive, independently
compilable/runnable domain slice (grouped by feature area — e.g. "Business
+ User entities and repos" — never one task per single class). No fixed
line-count bracket; each task is as long as its content genuinely needs.

## Phase folder layout

Each `docs/phase-NN-name/` folder contains:
- `00-task-breakdown.md` — one-line summary of every task in the phase, in
  order, with estimated time and prerequisites
- `task-01-*.md` through `task-NN-*.md` — one file per task, following the
  7-block structure above
```

- [ ] **Step 2: Verify no leftover reference to the old 8-block format**

```bash
grep -rn "why these lines\|Done when\|Touches row" docs/DOC-STANDARD-BACKEND.md
```

Expected: no matches (those are old-format terms; the new file uses
different section names).

- [ ] **Step 3: Commit**

```bash
git add docs/DOC-STANDARD-BACKEND.md
git commit -m "docs: write new 7-block DOC-STANDARD-BACKEND.md for the rewritten build guide"
```

---

### Task 3: Write `docs/phase-00-setup/` from scratch

**Files:**
- Create: `docs/phase-00-setup/00-task-breakdown.md`
- Create: `docs/phase-00-setup/task-01-*.md` through `task-0N-*.md` (N in 6-8 range)

**Interfaces:**
- Consumes: `docs/DOC-STANDARD-BACKEND.md` (Task 2's output) — the 7-block structure and sizing target.
- Produces: an environment-setup phase (JDK, Maven, MySQL, Node, Git, IDE, first commit) that phase-01 assumes is complete.

- [ ] **Step 1: Read real environment facts fresh**

Do not trust `CLAUDE.md`'s "Verified environment facts" table from memory —
re-run these on this machine right now and use only what they actually
print:

```bash
java -version
mvn -version
git --version
sc query MySQL80
```

Read `pom.xml` in full for the pinned `java.version`, `frontend-maven-plugin`
`nodeVersion`, and Maven wrapper version. Read `frontend/package.json` for
the real Vite/React version floor.

- [ ] **Step 2: Draft the task breakdown**

Group environment setup into 6-8 tasks by feature area, e.g.: JDK install +
verify, Maven, MySQL install + service, IDE setup, Git + GitHub repo init,
Node/frontend tooling verify, Postman/API client setup, final verify-all +
first commit. Write `00-task-breakdown.md` listing them in build order with
one-line summaries, estimated time each, and total phase time.

- [ ] **Step 3: Write each task file**

For each task in the breakdown, write a `task-NN-*.md` file following the
7-block structure exactly. Every version number, path, and command must be
verified against this actual machine (Step 1) — do not restate a number
from `docs/old-docs/` without re-checking it.

- [ ] **Step 4: Verify structural compliance**

```bash
grep -L "## 1. Header\|# .*Header" docs/phase-00-setup/task-*.md
```

Expected: every task file matches the 7-block headings from
`docs/DOC-STANDARD-BACKEND.md` — no file missing a block.

- [ ] **Step 5: Spot-check two version claims against the machine**

Pick two version numbers cited in the phase (e.g. the JDK version, the
`nodeVersion` pin) and re-run the corresponding command/`grep` from Step 1
to confirm the doc matches what's actually installed right now.

- [ ] **Step 6: Commit**

```bash
git add docs/phase-00-setup
git commit -m "docs: write phase 00 (environment setup) from scratch, 7-block format"
```

---

### Task 4: Write `docs/phase-01-project-init/` from scratch

**Files:**
- Create: `docs/phase-01-project-init/00-task-breakdown.md`
- Create: `docs/phase-01-project-init/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 00 complete (environment ready). `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: a running, empty Spring Boot project with the real `pom.xml`,
  `application.yml`, `.env.example`, `.gitignore`, and package skeleton in
  place, that phase-02 assumes exists.

- [ ] **Step 1: Read real source fresh**

Read, in full, right now: `pom.xml`, `src/main/resources/application.yml`,
`.env.example`, `.gitignore`, `frontend/.gitignore`,
`src/main/java/com/hyperlocal/delivery/DeliveryApplication.java`,
`src/main/resources/db/migration/.gitkeep`, and list the real
`com.hyperlocal.delivery` sub-package structure
(`find src/main/java/com/hyperlocal/delivery -maxdepth 1 -type d`).

- [ ] **Step 2: Draft the task breakdown**

Group into 6-8 tasks, e.g.: generate project (Spring Initializr equivalent),
IDE import, package structure, pom.xml dependencies, Git init + .gitignore,
application.yml, .env.example + env loading, Flyway migration folder, first
run + commit. Collapse to fit 6-8 — some of these may combine into one task.

- [ ] **Step 3: Write each task file**

Follow the 7-block structure. `DeliveryApplication.java`'s finished-code
block must include the real current file's
`@EnableConfigurationProperties(JwtProperties.class)` and
`TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` — with a note that these
are added incrementally as later phases need them, not present from the
start (same forward-reference approach the old guide used, verified
correct in this session's audit).

- [ ] **Step 4: Verify against real files**

```bash
diff <(sed -n '/```yaml/,/```/p' docs/phase-01-project-init/task-0*-application-yml*.md) src/main/resources/application.yml
```

(Adjust the file-glob to whichever task file has the application.yml
finished-code block.) Confirm the finished-code block's content is
byte-for-byte the real file (allowing for the diff tool's fence-line noise).
Do the same manual check for `.env.example` and `.gitignore`.

- [ ] **Step 5: Commit**

```bash
git add docs/phase-01-project-init
git commit -m "docs: write phase 01 (project init) from scratch, 7-block format"
```

---

### Task 5: Write `docs/phase-02-database-schema/` from scratch

**Files:**
- Create: `docs/phase-02-database-schema/00-task-breakdown.md`
- Create: `docs/phase-02-database-schema/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 01 complete. `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: the full database schema (all 9 application tables +
  `flyway_schema_history`) that phase-03's entities map onto.

- [ ] **Step 1: Read every real migration file fresh**

Read `src/main/resources/db/migration/V1__init_schema.sql` through
`V10__add_cancelled_status.sql` (10 files) in full, right now.

- [ ] **Step 2: Decide consolidation approach**

Decide, and document explicitly in `00-task-breakdown.md`, whether this
rewrite teaches the real V1-V10 incremental history directly (recommended:
simpler to keep accurate long-term, no consolidation-mapping table needed)
or again teaches a consolidated pedagogical version like the old guide did.
Either is acceptable — but the choice must be stated up front in the
breakdown, not left implicit.

- [ ] **Step 3: Draft the task breakdown and write each task file**

Group into 6-8 tasks by table/domain area (e.g. businesses+users, shipments
core, shipment_events+delivery_attempts, otp/auth-support tables, indexes
and constraints review, DBeaver verification). Every column, type,
constraint, and enum value must come from the real SQL files read in
Step 1.

- [ ] **Step 4: Verify against real migrations**

For every table the phase documents, confirm its column list against the
real `CREATE TABLE`/`ALTER TABLE` statements:

```bash
grep -A 20 "CREATE TABLE shipments" src/main/resources/db/migration/V1__init_schema.sql
```

(Repeat per table covered.) Confirm the shipment status ENUM lists all 8
real values including `CANCELLED` (added by V10).

- [ ] **Step 5: Commit**

```bash
git add docs/phase-02-database-schema
git commit -m "docs: write phase 02 (database schema) from scratch, 7-block format"
```

---

### Task 6: Write `docs/phase-03-jpa-entities/` from scratch

**Files:**
- Create: `docs/phase-03-jpa-entities/00-task-breakdown.md`
- Create: `docs/phase-03-jpa-entities/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 02 complete (real schema in place). `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: all 8 entities + all 8 repositories that phase-04 onward inject
  and query.

- [ ] **Step 1: Read every real entity and repository file fresh**

Read, in full: every `*.java` in `src/main/java/com/hyperlocal/delivery/model/`
and `src/main/java/com/hyperlocal/delivery/repository/`.

- [ ] **Step 2: Draft the task breakdown grouped by domain slice**

Target ~5-6 tasks, e.g.: "Business + User entities and repos," "Shipment +
ShipmentEvent entities and repos," "DeliveryAttempt entity and repo,"
"Auth-support entities: RefreshToken/OtpRecord/PendingRegistration," "enable
DDL validate + verify + commit." Each task's repo methods (including any
`@Query` JPQL) must be transcribed from the real file, not reconstructed
from memory of what a repository "usually" looks like.

- [ ] **Step 3: Write each task file**

Follow the 7-block structure. For `ShipmentStatus`, confirm all 8 enum
values including `CANCELLED`. Flag Phase 07 (CANCELLED) and Phase 11
(AgentInvite) as forward references where relevant, same as the old guide's
(verified-correct) approach.

- [ ] **Step 4: Verify with a real compile**

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
./mvnw.cmd clean compile -Dskip.installnodenpm=true -Dskip.npm=true -q
```

Expected: clean, no output — confirms the real entities/repos this phase
describes actually compile (they already exist in the app; this just
re-confirms the phase's factual claims are describing real, working code).

- [ ] **Step 5: Commit**

```bash
git add docs/phase-03-jpa-entities
git commit -m "docs: write phase 03 (JPA entities and repos) from scratch, 7-block format"
```

---

### Task 7: Write `docs/phase-04-security-jwt/` from scratch

**Files:**
- Create: `docs/phase-04-security-jwt/00-task-breakdown.md`
- Create: `docs/phase-04-security-jwt/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 03 complete. `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: the full JWT/security filter chain that phase-05's `AuthService`
  issues tokens into and every later controller's `@PreAuthorize` relies on.

- [ ] **Step 1: Read every real file fresh**

Read, in full: every file in `src/main/java/com/hyperlocal/delivery/security/`
(8 files: `AuthenticatedPrincipal`, `CustomUserDetails`,
`CustomUserDetailsService`, `JwtAuthFilter`, `JwtAuthenticationEntryPoint`,
`JwtProperties`, `JwtUtil`, `PublicApiPaths`), plus
`config/SecurityConfig.java`, `config/CorsConfigProperties.java`,
`service/LoginRateLimiter.java`, `exception/RateLimitExceededException.java`,
`dto/common/ApiError.java`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6-8 tasks, e.g.: JWT properties + JwtUtil, CustomUserDetails +
CustomUserDetailsService (get the per-request DB load right — every
request hits the DB via `loadUserByUsername`, do not describe it as
"only during login"), AuthenticatedPrincipal (the login-time DTO fed into
`JwtUtil.signAccess`, NOT the SecurityContext object — that's
`CustomUserDetails`), PublicApiPaths + JwtAuthFilter, SecurityConfig +
CORS, rate limiting, verify + commit. Confirm this distinction (a real
mismatch found and fixed in this session's audit) is correct in every task
that touches it.

- [ ] **Step 3: Verify the ApiError envelope shape**

Every 401/403 JSON example must use the real flat envelope from
`dto/common/ApiError.java` — confirm by reading that file — never a nested
`{"success": false, "error": {"code": ...}}` shape (a mistake found
repeatedly in the old guide).

- [ ] **Step 4: Commit**

```bash
git add docs/phase-04-security-jwt
git commit -m "docs: write phase 04 (security and JWT) from scratch, 7-block format"
```

---

### Task 8: Write `docs/phase-05-auth-and-otp/` from scratch

**Files:**
- Create: `docs/phase-05-auth-and-otp/00-task-breakdown.md`
- Create: `docs/phase-05-auth-and-otp/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 04 complete. `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: registration, OTP verification, login, and password-reset flows
  that phase-06 onward assume a logged-in user for.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `AuthService.java`, `OtpService.java`, `OtpRateLimiter.java`,
`OtpValidationResult.java`, `OtpRecordRepository.java`,
`OtpEmailService.java`/`ConsoleOtpEmailService.java`/`SmtpOtpEmailService.java`,
`MailConfig.java`, `PasswordResetMailer.java`/`MailLinkBuilder.java`/
`MailProperties.java`/`ConsoleMailService.java`/`SmtpMailService.java`, every
`dto/auth/*.java` record, `RegistrationRaceGuard.java`, `AuthController.java`,
`ErrorCode.java`, `GlobalExceptionHandler.java`.

- [ ] **Step 2: Draft the task breakdown grouped by flow**

Target 6-7 tasks: registration + OTP generation, OTP validation, login
(confirm `loginRateLimiter` calls all key off `normalizedEmail`, not raw
`req.email()` — this exact bug/fix is in the real current file, verify it
directly rather than assuming), refresh tokens, password reset, mail
sending abstraction, verify + commit.

- [ ] **Step 3: Write each task file, verifying business-logic details**

Confirm `invalidateActiveRecords`'s real return type and
`findActiveByEmailAndPurpose`'s real JPQL (no invented `ORDER BY` clauses)
by reading `OtpService.java`/`OtpRecordRepository.java` directly.

- [ ] **Step 4: Verify the login rate-limiter fix is correctly shown**

```bash
grep -n "loginRateLimiter\." src/main/java/com/hyperlocal/delivery/service/AuthService.java
```

Confirm every call site uses `normalizedEmail`. Confirm the task file
describing `login()` shows the same.

- [ ] **Step 5: Commit**

```bash
git add docs/phase-05-auth-and-otp
git commit -m "docs: write phase 05 (auth and OTP) from scratch, 7-block format"
```

---

### Task 9: Write `docs/phase-06-agent-management/` from scratch

**Files:**
- Create: `docs/phase-06-agent-management/00-task-breakdown.md`
- Create: `docs/phase-06-agent-management/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 05 complete (owner can log in). `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: agent CRUD + assignment logic that phase-07's shipment reassign
  and phase-11's invite flow build on.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `ShipmentStatusSets.java`, `AgentAssignmentService.java`,
`UserRepository.java` (`findAgentsByLoadAsc`/`lockById`),
`CreateAgentRequest`/`UpdateAgentRequest`/`AgentResponse`/`AgentSummaryDto.java`,
`AgentService.java`, `AgentController.java`,
`AgentNotFoundException`/`AgentHasActiveShipmentsException`/
`NoAgentsAvailableException.java`, `ErrorCode.java`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6-7 tasks: status sets + assignment query, agent DTOs (including the
real `activated` field and `TimeUtils.toIso` usage), agent service CRUD,
agent controller (6 endpoints in this phase; note the 7th, `POST
/{id}/invite`, as a Phase 11 forward reference), exceptions/error codes,
verify + commit (Postman walkthrough using the REAL flat
`{"status": "success", "data": ...}` envelope — the old guide invented a
`{"success": true}` shape here, verified wrong in this session, do not
repeat it).

- [ ] **Step 3: Verify the JPQL tiebreaker and response envelope**

```bash
grep -n "findAgentsByLoadAsc" -A 5 src/main/java/com/hyperlocal/delivery/repository/UserRepository.java
grep -n "record ApiSuccess" src/main/java/com/hyperlocal/delivery/dto/common/ApiSuccess.java
```

Confirm the task file's JPQL matches exactly (including the `u.id ASC`
tiebreaker) and every JSON example in the phase uses `ApiSuccess`'s real
field names (`status`, `data`).

- [ ] **Step 4: Commit**

```bash
git add docs/phase-06-agent-management
git commit -m "docs: write phase 06 (agent management) from scratch, 7-block format"
```

---

### Task 10: Write `docs/phase-07-shipment-lifecycle/` from scratch

**Files:**
- Create: `docs/phase-07-shipment-lifecycle/00-task-breakdown.md`
- Create: `docs/phase-07-shipment-lifecycle/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 06 complete. `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: the full shipment state machine + 13-endpoint controller that
  phase-08's delivery attempts and phase-09's analytics query against.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `ShipmentService.java`, `ShipmentController.java`, every
`dto/shipment/*.java` record, `ShipmentEvent`-related classes,
`InvalidStateForAttemptException` and other shipment exceptions,
`ShipmentStatusSets`, `TimeUtils.java`, `ShipmentRepository.java`
(`lockById`, `findWithDetailById`), and `docs/concepts/state-machines.md`
(to keep it in sync — update it if this rewrite changes how the state
machine is explained, using real method/parameter names like `lockById`
and `to`).

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 7-8 tasks (this is the largest phase): create + list, agent
transition endpoints (pickup/start-transit/out-for-delivery/deliver/return),
fail endpoint, reassign, cancel + delete, get + mine (dual-role access),
verify + commit. Confirm the real route names exactly — the endpoint is
`POST /{id}/start-transit`, not `/{id}/transit` (a mistake found and fixed
in this session).

- [ ] **Step 3: Verify the full endpoint list**

```bash
grep -n "@\(Get\|Post\|Put\|Delete\|Patch\)Mapping" src/main/java/com/hyperlocal/delivery/controller/ShipmentController.java
```

Confirm all 13 real endpoints are documented with their real paths.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-07-shipment-lifecycle
git commit -m "docs: write phase 07 (shipment lifecycle) from scratch, 7-block format"
```

---

### Task 11: Write `docs/phase-08-delivery-attempts/` from scratch

**Files:**
- Create: `docs/phase-08-delivery-attempts/00-task-breakdown.md`
- Create: `docs/phase-08-delivery-attempts/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 07 complete. `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: delivery-attempt recording that phase-09's analytics reads.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `FailureReason.java`, `DeliveryAttempt.java`,
`DeliveryAttemptRepository.java`, `DeliveryAttemptService.java`,
`DeliveryAttemptController.java`, `DeliveryAttemptDto.java`,
`InvalidStateForAttemptException.java`, `CreateAttemptRequest`/
`FailAttemptRequest.java`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6 tasks: entity + repo, DTOs (real `TEXT` column for `notes`, not
`VARCHAR(1000)` — a mistake found and fixed in this session), service logic
(MAX_ATTEMPTS=3, RETURNED transition, `status != OUT_FOR_DELIVERY` guard),
controller, exceptions, verify + commit. Use the real `FailureReason` enum
values (`CUSTOMER_ABSENT`, `ADDRESS_NOT_FOUND`, `REFUSED`, `DAMAGED`,
`OTHER`) in every curl example — the old guide invented
`CUSTOMER_UNAVAILABLE`/`WRONG_ADDRESS` here, verified wrong and fixed this
session; do not repeat that mistake.

- [ ] **Step 3: Verify the enum and column type**

```bash
cat src/main/java/com/hyperlocal/delivery/model/FailureReason.java
grep -n "notes" src/main/resources/db/migration/V1__init_schema.sql
```

- [ ] **Step 4: Commit**

```bash
git add docs/phase-08-delivery-attempts
git commit -m "docs: write phase 08 (delivery attempts) from scratch, 7-block format"
```

---

### Task 12: Write `docs/phase-09-analytics-reports/` from scratch

**Files:**
- Create: `docs/phase-09-analytics-reports/00-task-breakdown.md`
- Create: `docs/phase-09-analytics-reports/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 08 complete. `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: the analytics/reports API that phase-10's production checklist
  references.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `TimeUtils.java`, `ResponseBuilder.java`, the six analytics
query methods in `ShipmentRepository.java` (`agentStats`, `dailyVolumeRaw`,
`overviewRaw`, `onTimeRateRaw`, `firstAttemptSuccessRaw`,
`failureReasonBreakdownRaw`), `AnalyticsService.java`,
`AnalyticsController.java`, `ReportController.java`, `CsvExportWriter.java`,
`ApiSuccess.java`, and every DTO under `dto/analytics/` and `dto/reports/`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6-7 tasks: TimeUtils + timezone handling, repository queries,
overview/trend DTOs, analytics controller, CSV export + report controller,
verify + commit. Use the real flat `{"status": "success", "data": ...}`
envelope in every example — this exact fictional-envelope mistake was found
in two separate task files here in this session's audit; verify against
`ApiSuccess.java` directly before writing any JSON example.

- [ ] **Step 3: Verify query method count and envelope**

```bash
grep -n "public.*Raw(\|public.*Stats(" src/main/java/com/hyperlocal/delivery/repository/ShipmentRepository.java
```

Confirm the phase documents exactly six analytics query methods, not five.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-09-analytics-reports
git commit -m "docs: write phase 09 (analytics and reports) from scratch, 7-block format"
```

---

### Task 13: Write `docs/phase-10-production-readiness/` from scratch

**Files:**
- Create: `docs/phase-10-production-readiness/00-task-breakdown.md`
- Create: `docs/phase-10-production-readiness/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 09 complete. `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: the production-ready configuration (scheduling, OpenAPI,
  startup validation, SPA fallback, CORS profiles) — the last backend
  phase before invites.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `SchedulingConfig.java`, `OpenApiConfig.java`,
`StartupValidator.java`, `HealthController.java`,
`SpaFallbackController.java`, `WebConfig.java`,
`ShipmentStatusConverter.java`, `application.yml`, `application-dev.yml`,
`application-prod.yml`, `PublicApiPaths.java` (the `/api/health` entry),
`frontend/package.json`, `frontend/vite.config.ts` (the REAL current
frontend tooling: React 19, Vite 7, TypeScript config, TanStack Router,
Tailwind v4/Radix — post-redesign, verified accurate this session).

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6-7 tasks: scheduling + async config, OpenAPI + health, startup
validation, SPA fallback + WebConfig + ShipmentStatusConverter, CORS
profiles (dev/prod), frontend build tooling overview (backend-serving
contract only — SPA fallback, static resource handling — not frontend
implementation detail), verify + commit.

- [ ] **Step 3: Verify config claims**

```bash
grep -n "access-ttl-minutes\|allowed-origins" src/main/resources/application*.yml
```

Confirm the dev/prod profile differences match exactly.

- [ ] **Step 4: Commit**

```bash
git add docs/phase-10-production-readiness
git commit -m "docs: write phase 10 (production readiness) from scratch, 7-block format"
```

---

### Task 14: Write `docs/phase-11-agent-invites/` from scratch

**Files:**
- Create: `docs/phase-11-agent-invites/00-task-breakdown.md`
- Create: `docs/phase-11-agent-invites/task-01-*.md` through `task-0N-*.md`

**Interfaces:**
- Consumes: Phase 06 complete (agent management) — this phase depends only
  on Phase 06, same as the old guide. `docs/DOC-STANDARD-BACKEND.md` structure.
- Produces: the final phase; nothing later depends on it.

- [ ] **Step 1: Read every real file fresh**

Read, in full: `V9__agent_invites.sql`, `AgentInvite.java`,
`AgentInviteRepository.java`, `MailLinkBuilder.java`, `MailProperties.java`,
`AgentInviteMailer.java`/`ConsoleAgentInviteMailer.java`/
`SmtpAgentInviteMailer.java`, `MailConfig.java`, the
`app.mail.invite-link-base-url`/`invite-ttl-hours` keys in
`application.yml`, `InvalidInviteTokenException.java`,
`InviteResponse.java`/`InvitePreviewResponse.java` (confirm `expiresAt` is
a `String` via `TimeUtils.toIso`, not raw `LocalDateTime`),
`AcceptInviteRequest.java`, `AgentInviteService.java`,
`AgentController.java`'s `POST /{id}/invite`, `AuthController.java`'s
`GET /invite/{token}` and `POST /accept-invite`, `PublicApiPaths.java`.

- [ ] **Step 2: Draft the task breakdown and write each task file**

Target 6 tasks: migration + entity + repo, mail sending abstraction, invite
issue + preview service logic, accept-invite service logic, controller
endpoints + public path config, verify + commit. In the verify/commit
task's closing pointer to frontend UI (if any), name the real components —
`InviteSheet` inside `frontend/src/components/owner/OwnerLive.tsx` and
`LiveSetup` in `frontend/src/components/auth/AuthLive.tsx` routed at
`/agent-setup` — never invented file paths (a mistake found and fixed in
this session).

- [ ] **Step 3: Verify the DTO types and public paths**

```bash
grep -n "expiresAt" src/main/java/com/hyperlocal/delivery/dto/invite/InviteResponse.java src/main/java/com/hyperlocal/delivery/dto/invite/InvitePreviewResponse.java
grep -n "invite" src/main/java/com/hyperlocal/delivery/security/PublicApiPaths.java
```

- [ ] **Step 4: Commit**

```bash
git add docs/phase-11-agent-invites
git commit -m "docs: write phase 11 (agent invites) from scratch, 7-block format"
```

---

## Final check (after Task 14)

- [ ] Confirm `docs/` top level contains only: `old-docs/`, `concepts/`,
  `ARCHITECTURE.md`, `API-REFERENCE.md`, `DOC-STANDARD-BACKEND.md`, `superpowers/`,
  and the 12 new `phase-*` folders — no leftover old-format file.
- [ ] Run `find docs -maxdepth 2 -name "00-task-breakdown.md"` and confirm
  all 12 phases have one.
- [ ] Spot-check 3 phases at random by re-running one grep-verification
  command from that phase's task above, to confirm nothing drifted between
  writing and this final pass.
