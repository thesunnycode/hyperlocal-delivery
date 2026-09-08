# Hyperlocal frontend

React 18 + TypeScript + Vite. Talks to the multi-tenant Spring Boot / MySQL
backend and is bundled into the same jar by `frontend-maven-plugin`, so a
production deploy serves it same-origin from `src/main/resources/static`.

## Getting started

```
npm install
npm run dev                 # http://localhost:5173, proxies /api to :8080
npm run build               # → ../src/main/resources/static (see vite.config.js)
npm test                    # vitest, happy-dom
npm run typecheck           # tsc --noEmit
npm run lint                # eslint src/
```

`.env` is only needed if the API is on a different origin — copy `.env.example`
and set `VITE_API_BASE_URL`. To point the dev proxy somewhere other than
`http://localhost:8080`, set `VITE_API_PROXY_TARGET` as a shell env var (not in
`.env` — `vite.config.js` reads it from `process.env`).

## The four surfaces

One SPA, four audiences, and the CSS is scoped per surface so a change to one
cannot leak into another. Each stylesheet declares its own palette on a root
class; there is no shared token sheet.

| Scope | Who | Routes |
|---|---|---|
| `.auth` | owner signing up, rider setting a first password | `/login`, `/register`, `/forgot-password`, `/agent-setup` |
| `.ow` | shop owner — the operations console | `/owner/*`, including `/owner/reports/*` |
| `.rp` | the reporting section, rendered **inside** the owner shell | `/owner/reports/{overview,trend,agents}` |
| `.ag` | delivery rider, phone-first | `/agent/*` |
| `.tk` | customer, public, no auth | `/track/:token` |

`/admin/*` used to be a separate shell for the same signed-in owner. It is now
a set of redirects into `/owner/reports/*` — see the 2026-09-07 entry in
`docs/DRIFT-LEDGER.md`.

## Layout

```
src/
  api/          One file per resource. Every network call goes through these.
                API_CONTRACT.md documents the endpoints; types/api.ts mirrors
                the Java records field by field.
  lib/          apiClient (fetch wrapper, envelope unwrapping, paging, token
                storage), AuthContext, ToastContext, FatalErrorContext,
                useModalBehaviour (Escape / focus trap / focus restore —
                every overlay uses it).
  utils/        statusMachine.ts (labels, steps, legal transitions — mirrors
                the backend enum, no CREATED state), format.ts.
  components/   AuthShell/AuthSteps/AuthHandoff/OtpInput/ResendCodeButton
                (auth), RiderIllustration (auth panel + tracking page),
                ShipmentDetailPanel, CreateShipmentModal, ReassignAgentModal,
                AgentInviteSheet, ConfirmDialog, ToastStack, DayBars (charts),
                EdgeStateCard (404/403/500/expired), ProtectedRoute.
  layouts/      OwnerLayout — the single console shell, sidebar on desktop and
                a bottom tab bar on phones. ReportingFrame mounts the
                reporting pages inside it.
  pages/        customer/ · agent/ · owner/ · admin/ (reporting) and the four
                unauthenticated pages at the top level.
  styles/       base.css (globals only) + one file per scope: auth, owner,
                reports, agent, track, rider, edge, overlay.
```

## Conventions worth knowing before you edit

- **One content measure.** `--ow-measure` (1180px) in `owner.css`, mirrored as
  `--rp-measure`. Toolbars stay full-bleed but cap their content with
  `--ow-gutter`. Do not introduce a new page width — five different ones is
  what made wide screens look unstructured. The account form's 620px is the
  one deliberate exception, commented at the rule.
- **The state machine is centralized** in `utils/statusMachine.ts`. If the
  backend enum or its legal transitions change, that is the file to update.
- **Owner mutations are exactly two**: return a failed shipment to the queue,
  and change the assigned agent. The owner never advances a shipment forward.
- **Agent mutations are one legal forward step at a time** — never a status
  dropdown. Failure always goes through the mandatory-reason sheet before the
  status moves.
- **Toasts are confirmation only.** They never carry the only copy of
  anything; the resting page state should already say the same thing.
- **No CSS framework and no inline design tokens.** Extend the scope's
  stylesheet rather than hand-rolling colours or spacing.
- **`grid-column` is not a layout tool here.** Positioning a flat list of
  children by class leaves holes the moment the two sides differ in count;
  use real wrapper elements. See the tracking-page note in the drift ledger.

`API_CONTRACT.md` is the integration surface with the backend, and
`docs/DRIFT-LEDGER.md` records every change that could invalidate a doc.
