# Hyperlocal Delivery — project instructions

Spring Boot **4.1.1** / Java 17 target / MySQL 8 / Flyway, with a React frontend
in `frontend/` bundled into the same jar by `frontend-maven-plugin`.

---

## The documentation set

`docs/` holds one build guide: twelve phase folders, `phase-00-setup` through
`phase-11-agent-invites`, each with a `00-task-breakdown.md` and per-task
`task-NN-*.md` files, plus `concepts/` and `mockups/`.

`phase-11-agent-invites` was added after the original run and documents the
agent invite flow (migration V9 through the endpoints). It depends only on
phase 06, so it can be built straight after agent management.

| File | What it is |
|---|---|
| `docs/DOCS-OVERVIEW.md` | Entry point — all nine phases at a glance, dependency graph, timeline |
| `docs/SYSTEM-ARCHITECTURE.md` | Diagrams of the finished system |
| `docs/DRIFT-LEDGER.md` | **Read before editing any doc.** One entry per code change that could make a doc wrong |
| `docs/AUDIT-PROMPT.md` | The standing prompt for auditing the guide against real code, phase by phase |
| `docs/EVALUATION-AND-RESUME.md` | Honest project assessment and CV framing |
| `docs/audits/` | Past audit reports |

### Rules when working on the docs

- **Add a `DRIFT-LEDGER.md` entry in the same commit** as any code change that
  could invalidate a doc — a renamed method, a changed DTO shape, a new config
  key. That file exists so the next audit does not rediscover the same rename
  in three separate phases.
- **Never state a version, path, command or output you have not verified**
  against this machine or this repository. Check `pom.xml` and the real source,
  not memory.
- Dated entries in `DRIFT-LEDGER.md` record what was true when written. Do not
  rewrite them; add a new entry instead.
- Audit **one phase per pass**, not all at once. That is what caught the most
  issues in practice — see `docs/AUDIT-PROMPT.md`.

---

## Build and run

```bash
# JAVA_HOME is NOT set on this machine, and mvnw.cmd requires it.
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"

./mvnw.cmd clean compile -Dskip.installnodenpm=true -Dskip.npm=true   # backend only, fast
./mvnw.cmd spring-boot:run                                            # run the app
./mvnw.cmd test                                                       # runs BOTH Java and React suites
```

`mvn test` runs the frontend test suite too, because `frontend-maven-plugin`
binds `npm test` to the `test` phase. Vite output mid-build is expected.

### Verified environment facts

| Fact | Value |
|---|---|
| JDK | Temurin `openjdk 21.0.11`, at `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` |
| `JAVA_HOME` | **not set** at any scope — so `./mvnw.cmd` fails without the export above |
| System Maven | `3.9.16`, home `C:\tools\maven`; the wrapper pins `3.9.9` |
| MySQL | Server 8.0, Windows service `MySQL80`. Client not on `PATH` in Git Bash; it is at `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe` |
| Git | `2.54.0.windows.1` |
| Node | never installed manually — `frontend-maven-plugin` fetches `v20.19.0` (bumped from `v20.11.0`; the current frontend's Vite requires `^20.19.0 \|\| >=22.12.0` — see `docs/DRIFT-LEDGER.md` 2026-09-27) |

---

## Repository conventions

- Commit messages use `type(scope): summary` — `feat`, `fix`, `docs`, `chore`.
- Default branch is `master`. No git remote is configured — this is a
  local-only repository; check `git branch`/`git status` for the actual
  current branch rather than assuming one here.
- Never commit: `target/`, secrets, `.claude/worktrees/` (an 11 MB repo copy).
