# Hyperlocal — frontend redesign brief

You are being handed the **complete source** of a working React frontend and
asked to redesign its UI/UX to a product standard.

Everything in this folder compiles, passes 54 tests, and talks to a live Spring
Boot API today. **Nothing here is a mockup or a toy.** Read §3 (Invariants)
before you write a line — breaking anything in it produces a redesign that looks
better and does not work, which is worse than no redesign.

---

## 0. How this works — mockup first, code second

**Do not touch the source files on your first pass.**

### Pass one — the clickable mockup

Build a **single self-contained HTML file** (inline CSS and JS, no build step,
no external dependencies beyond a Google Fonts link) that presents your proposed
design for **all 21 screens**, and let me click through it.

It must:

- Show every screen in §5, reachable by clicking — a nav or index that jumps
  between them, and, where a screen has a real flow (login → OTP → done,
  shipment row → detail, create form → success), let me walk the flow.
- Be **interactive, not a slide deck of images**: real hover states, focus
  rings, open and close the modals and the side sheet, toggle the filters,
  expand the detail panel, show a toast.
- Include a **states switcher** on the screens that have them — populated,
  loading skeleton, empty, filtered-empty, blocked, error — because those are
  half the design work and the half usually skipped (§6).
- Show **both breakpoints**: desktop and a phone frame. The rider app and the
  customer tracking page are phone-first and must be shown that way.
- Open with a short **design-system panel**: the palette with hex values and
  their measured contrast ratios, the type scale, the spacing scale, the
  elevation and radius rules, and the motion rules.
- Use **realistic Indian content** — names like Ananya Iyer, Rehan Qureshi;
  addresses like "3rd Cross, Malleshwaram, Bengaluru 560003"; ₹ amounts;
  +91 phone numbers. No lorem ipsum, no "John Doe", no "Card Title".

Then **stop and wait.** Tell me what you changed and why, and let me click
through it. Do not begin editing source files.

### Pass two — apply it

Only when I reply **"continue"**: apply the approved design to every relevant
file and folder here, editing the real files in place. Change as many files as
the design needs — this is a revamp, so expect to touch every stylesheet, every
page and most components. Do not leave a screen half-migrated between the old
design and the new one. Then write `REDESIGN-NOTES.md` (§7) at the root of this
folder.

If you cannot write to these files directly, return the complete folder as a
drop-in zip with the same structure instead, and say so.

If I ask for changes to the mockup first, revise the mockup and wait again.

---

## 1. The product, in one paragraph

A small shop owner in an Indian town delivers orders with one to five riders on
motorbikes. Today they coordinate on WhatsApp and phone calls, and their
customers ring them to ask "where is my order". This app replaces that: the
owner creates a shipment, it is auto-assigned to the least-loaded active rider,
the rider moves it through a fixed status flow on a phone, and the customer
watches a public tracking page from a link — **no app to install, no login**.

Three audiences, three densities, and they must not look like the same screen:

| Who | Where | Context | Density |
|---|---|---|---|
| **Owner** | Desktop, sitting down | Triage: what needs me, what is moving | Dense. Someone works here all day. |
| **Rider (agent)** | Phone, one hand, outdoors, moving, in sunlight | "What is my next stop, and mark it done" | Sparse, big targets, glanceable. |
| **Customer** | Phone, from a WhatsApp link | One question: where is my order | Calm, roomy, zero chrome. One screen. |

A fourth mode lives inside the owner console: **reporting**, three read-only
analytics screens. Quiet, data-forward, no decoration competing with the data.

---

## 2. Art direction

**Think like a product design team shipping v1 of a real SaaS, not like someone
dressing up a college project.** The difference is not decoration — it is that
every state is designed, the type scale is deliberate, the spacing is on a
system, and nothing is left to a default.

### Colour

- **Minimal, subtle, calming.** A restrained neutral foundation carrying most
  of the interface, one confident brand colour used sparingly for the primary
  path, and a small semantic set for status. Not funky, not neon, not a
  gradient showcase, no glassmorphism, no purple-to-pink.
- **Proper hierarchy, and it must be legible as hierarchy.** Define and use
  distinct roles rather than a pile of hexes: page background, raised surface,
  sunken surface, hairline border, strong border, primary text, secondary text,
  tertiary/muted text, brand, brand-hover, brand-subtle-background, and the
  semantic set. Every one is a decision you can defend, and the same role does
  the same job on every screen.
- **Colour is never the only carrier of meaning.** The seven statuses need a
  label and ideally a shape, not just a hue — riders are outdoors in sunlight
  and some users are colour-blind.
- Give me the palette as **4–6 named colours with hex values**, plus the
  neutral ramp, plus measured contrast ratios for every text pair you use.
- **Dark mode is optional.** If you do it, do it completely and honestly across
  all six stylesheets; a half-converted dark mode is worse than none.

### Typography

Pair a display face and a body face deliberately, with a real scale — not
"h1 is big, p is small". State the scale, the weights, the tracking, and the
line heights, and apply them consistently. The most important block on a screen
must be typed like the most important block on that screen; the current design
got this backwards more than once.

### Motion and interactivity

**Yes to animation — but as feedback, not as decoration.** The bar: would a
product designer defend this transition in review?

Good candidates here:
- State transitions on the status timeline — a shipment advancing should feel
  like it advanced.
- The detail panel opening beside the list; the create sheet sliding in.
- Skeleton → content, without a jarring reflow.
- Button press, toggle, and focus micro-interactions; toast enter and exit.
- The customer tracking page's live status — this is the one screen where a
  little delight is genuinely earned, because it is the shop window and the
  person watching it is anxious.

Rules: fast (mostly 120–240ms), one easing family, nothing that blocks input,
nothing that loops forever in the corner of a working screen, and **every
animation respects `prefers-reduced-motion`**. No parallax, no scroll-jacking,
no confetti.

### Calibrate away from the defaults

Avoid the three looks that AI design converges on regardless of subject: warm
cream + high-contrast serif + terracotta accent; near-black + one acid-green or
vermilion accent; and broadsheet hairline-rule newspaper columns. The current
design is a warm off-white with a deep blue and Instrument Serif — competent
and a little safe. **You may keep it, evolve it, or replace it**, but if you
change direction, change it completely and coherently across all six scoped
stylesheets, not by dropping a new accent on the same bones.

Two things to design *for*, whatever direction you pick:

1. **Cheap Android phones, mobile data, outdoors, in sunlight.** Contrast and
   target size are functional requirements here, not compliance boxes.
2. **The customer tracking page is the only screen a stranger ever sees.** It
   is the product's shop window and should carry the most character.

### Where this design is coming from

An audit found these; they are the failure modes to design *away from*, not
just patch. Per-screen audit reports with measured numbers exist outside this
folder in `docs/audits/` — ask for them if you want the detail.

- **A container pinned to one side.** The page gutter was right-side-only, so
  at 1920 every console screen had 22px of air on the left and 502px on the
  right. Fixed — but the lesson stands: pick a measure, centre it, make every
  screen agree on it.
- **Two patterns stacked for one job.** First run showed an onboarding
  checklist *and* a generic empty state, three call-to-actions for one task.
- **A disabled primary button as the answer to a precondition**, with its only
  explanation in a `title` tooltip no keyboard or touch user can reach.
- **`opacity` as a disabled style**, dimming labels to 1.65:1.
- **Inverted type hierarchy** — the most important block set in the smallest
  type on the screen.

---

## 3. Invariants — do not change these

Contracts with a running backend, a build, and a test suite.

### 3.1 API

- Base URL is **`/api`** (same-origin; `VITE_API_BASE_URL` overrides).
- Every response is enveloped: `{ status: 'success', data: … }`, paged adds
  `{ pagination: … }`, errors are `{ status: 'error', code, message }`.
- **`src/lib/apiClient.ts` is the only place that calls `fetch`.** Keep it that
  way. Its exports — `apiFetch`, `apiFetchPage`, `fetchAllPages`, `ApiError`,
  `OFFLINE_MESSAGE`, `getToken`, `getRole`, `getStoredUser`, `setSession`,
  `clearSession`, `API_BASE_URL` — are imported across the app and asserted by
  tests.
- `OFFLINE_MESSAGE` must stay free of developer-only language: a deployed shop
  owner sees it, and two tests assert it names no port, host, `ECONNREFUSED`,
  proxy or "backend".
- Every endpoint and shape is in **`API_CONTRACT.md`**. Do not invent fields.
  If a design needs data the API does not return, say so in your notes rather
  than faking it.

### 3.2 Auth and storage keys

Exact strings: **`hl_token`**, **`hl_role`**, **`hl_user`**,
**`hl.setup.dismissed`**, **`hl.tracking.linkCopied`**.
Roles are **`OWNER`** and **`AGENT`**.

### 3.3 Routes

Every path in §5 must still resolve, including the `/admin/*` and
`/owner/login` redirects — old links are in the wild. `ProtectedRoute` guards
by role; `RedirectIfAuthenticated` bounces a signed-in user off the auth pages.

### 3.4 The status machine

Seven statuses, exact keys: `assigned`, `picked_up`, `in_transit`,
`out_for_delivery`, `delivered`, `failed`, `returned`. Order is fixed; the
machine lives in `src/utils/statusMachine.ts`.

Rules the UI must keep expressing:
- Only the **rider** advances a shipment forward. The owner never does.
- The owner's only two mutations are **reassignment** and **failed → assigned**.
- `delivered` and `returned` are terminal. `failed` is terminal for the rider
  but not for the owner.
- A shipment is **auto-assigned on creation**, so at least one active agent must
  exist before the first shipment can be made. This is the app's one real
  precondition, and the whole first-run experience turns on it.

### 3.5 Build

- Vite builds to **`../src/main/resources/static`** — the Spring Boot jar serves
  it same-origin. Do not change `build.outDir`.
- The dev proxy sends `/api` to `http://localhost:8080` and answers `503` with
  the error envelope when the backend is down. Keep that behaviour.
- Stack is **React 18 + TypeScript + React Router 6 + Vite 5**, `lucide-react`
  for icons. **Do not add a UI framework, CSS-in-JS library, Tailwind, or a
  component library.** Plain CSS files, as today. Motion in CSS where it can be;
  add a JS animation dependency only if it genuinely earns its place, and say
  why in the notes.
- `npx tsc --noEmit`, `npx eslint src/` and `npx vitest run` must all pass.
  **54 tests exist and must still pass.** Update a test only if you deliberately
  changed the behaviour it asserts, and say so.

### 3.6 Stylesheet architecture

Six scoped stylesheets, each owning a prefix, plus a small global:

| File | Scope | Screens |
|---|---|---|
| `base.css` | element defaults, `.sr-only`, `.skip-link` | global |
| `auth.css` | `.auth` | login, register, forgot password, agent setup |
| `owner.css` | `.ow` | owner console + sidebar |
| `reports.css` | `.rp` | the three reporting screens |
| `agent.css` | `.ag` | the rider app |
| `track.css` | `.tk` | customer tracking |
| `rider.css` | `.rider` | the illustration |
| `edge.css` | `.es` | 404 and fatal screens |
| `overlay.css` | `.cd` / `.ts` | dialogs, sheets, toasts |

Keep the scoping — it is what lets one section change without the others
shifting. Put shared tokens in **one** place and have each scope consume them;
do not duplicate a palette six times.

---

## 4. Quality floor

Non-negotiable. Check them yourself before you hand the work back.

- **Contrast:** every text pair ≥ 4.5:1 (≥ 3:1 for ≥18.66px bold / ≥24px).
  Composite any `opacity` before measuring. Focus rings and meaningful borders
  ≥ 3:1.
- **Keyboard:** everything actionable reachable and visibly focused. No
  explanation living only in `title`. Nothing important behind hover alone.
- **Semantics:** one `h1` per screen, headings descend, the primary block of a
  screen is a heading. Icon-only controls have accessible names; decorative
  icons are `aria-hidden`. Modals trap focus and restore it on close.
- **Responsive:** no horizontal scroll at 375px; touch targets ≥ 44px there.
  Check 1920, 1440, 834 and 375.
- **Motion:** everything respects `prefers-reduced-motion`.
- **Colour is never the only carrier of meaning.**
- **Every state is designed** (§6), not just the populated one.

---

## 5. Screens

| # | Route | File | Scope | Notes |
|---|---|---|---|---|
| 1 | `/login` | `pages/LoginPage.tsx` | auth | Both roles sign in here. |
| 2 | `/register` | `pages/RegisterPage.tsx` | auth | Two steps: details, then an emailed OTP. |
| 3 | `/forgot-password` | `pages/ForgotPasswordPage.tsx` | auth | OTP, then new password. |
| 4 | `/agent-setup` | same, `mode="setup"` | auth | A rider's first password, from an invite link. |
| 5 | `/track/:token` | `pages/customer/CustomerTrackingPage.tsx` | tk | **Public. No auth. The shop window.** |
| 6 | `/owner/shipments` | `pages/owner/OwnerShipmentsPage.tsx` | ow | Queue + resting pane. The main screen. |
| 7 | `/owner/shipments/:id` | `components/ShipmentDetailPanel.tsx` | ow | Master–detail; the list stays visible. |
| 8 | create shipment | `components/CreateShipmentModal.tsx` | overlay | |
| 9 | reassign | `components/ReassignAgentModal.tsx` | overlay | |
| 10 | `/owner/register` | `pages/owner/OwnerRegisterPage.tsx` | ow | Dense table, CSV export, sortable. Desktop only. |
| 11 | `/owner/agents` | `pages/owner/OwnerAgentsPage.tsx` | ow | Roster. |
| 12 | invite agent | `components/AgentInviteSheet.tsx` | overlay | |
| 13 | `/owner/account` | `pages/owner/OwnerAccountPage.tsx` | ow | Form page; per-card save. |
| 14 | `/owner/reports/overview` | `pages/admin/AdminOverviewPage.tsx` | rp | |
| 15 | `/owner/reports/trend` | `pages/admin/AdminTrendPage.tsx` | rp | `DayBars` chart. |
| 16 | `/owner/reports/agents` | `pages/admin/AdminAgentPerformancePage.tsx` | rp | |
| 17 | `/agent/assignments` | `pages/agent/AgentAssignmentsPage.tsx` | ag | **The rider's home. Phone-first.** |
| 18 | `/agent/shipments/:id` | `pages/agent/AgentShipmentDetailPage.tsx` | ag | Where the status is advanced. |
| 19 | `/agent/account` | `pages/agent/AgentAccountPage.tsx` | ag | |
| 20 | `*` | `pages/NotFoundPage.tsx` | es | |
| 21 | fatal | `components/OwnerFatalScreen.tsx`, `EdgeStateCard.tsx` | es | |

Furniture, redesign once each: `layouts/OwnerLayout.tsx` (sidebar + mobile tab
bar), `layouts/ReportingFrame.tsx`, `components/ToastStack.tsx`,
`ConfirmDialog.tsx`, `OtpInput.tsx`, `AuthShell.tsx`, `AuthSteps.tsx`,
`RiderIllustration.tsx`.

## 6. States — every screen, not just the happy one

1. **Populated** — normal data.
2. **Loading** — a skeleton matching the real layout, and no copy asserting a
   number before it is known.
3. **Empty, nothing ever created** — an invitation to act. One primary action.
4. **Empty because a filter excludes everything** — a way back, *not* a setup
   guide. These two are different screens.
5. **Blocked by a precondition** — no active agent, so no shipment can exist.
   Say which, and lead to fixing it.
6. **Error, actionable** — a 4xx with a message, shown inline near its cause.
7. **Error, fatal** — a 5xx or an unreachable API. Full screen, and it must not
   blame the wrong thing.
8. **Not found** — a stale or hand-typed id, scoped to the record, not the app.
9. **Dense** — 200+ rows, long names and addresses, all seven statuses.
10. **Wrong role** — the other role's URL.

---

## 7. What to hand back

**Pass one:** the single-file clickable mockup, plus a short summary of the
direction and what you rejected. Then stop and wait.

**Pass two** (only after I say continue): the edits applied in place across this
folder, plus **`REDESIGN-NOTES.md`** at its root containing:

- The direction in a paragraph, and what you rejected.
- The token system: named colours with hex, the neutral ramp, the type pairing
  and scale, the spacing scale, radius and elevation rules, and the motion rules
  (durations and easings).
- Contrast measurements for the new palette — the pairs and their ratios.
- A per-screen list of what changed and why.
- **Anything you changed in §3**, called out loudly, with the reason.
- Any test you modified, and why the old assertion no longer holds.
- Anything you could not do, and what blocked you.

Do not silently drop a feature because it did not fit the new layout. Every
control that exists today does something an owner or a rider needs; if one is in
the way, say so in the notes and propose where it should go.

---

## 8. Verifying your own work

Before you hand back pass two, run these in the browser on each redesigned
screen and fix what they report. The current code passes all of them, so
anything failing is a regression you introduced.

```js
// A. Container symmetry — the "why does this not look like a website" test.
// left and right should match. A 22 / 502 pair is the defect.
(() => {
  const pane = document.querySelector('.ow-main, main') ?? document.body;
  const p = pane.getBoundingClientRect();
  return [...pane.querySelectorAll('.ow-head, .ow-bar, .ow-acct > *, .ow-state, .ow-card')]
    .slice(0, 12).map((el) => { const r = el.getBoundingClientRect();
      return { el: el.className.slice(0, 28), left: Math.round(r.left - p.left),
               right: Math.round(p.right - r.right), width: Math.round(r.width) }; });
})();

// B. Every contrast failure on screen, composited through opacity.
// Must return an empty array.
(() => {
  const lum = (c) => { const [r,g,b] = c.match(/\d+\.?\d*/g).slice(0,3).map((v)=>{
    v=+v/255; return v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4; });
    return 0.2126*r+0.7152*g+0.0722*b; };
  const bgOf = (el) => { for (let n=el;n;n=n.parentElement){ const c=getComputedStyle(n).backgroundColor;
    if (c && !c.includes('rgba(0, 0, 0, 0)')) return c; } return 'rgb(255,255,255)'; };
  const opOf = (el) => { let o=1; for(let n=el;n;n=n.parentElement) o*=+getComputedStyle(n).opacity; return o; };
  const mix = (f,b,a) => { const F=f.match(/\d+\.?\d*/g).map(Number), B=b.match(/\d+\.?\d*/g).map(Number);
    return `rgb(${[0,1,2].map(i=>Math.round(F[i]*a+B[i]*(1-a))).join(',')})`; };
  const out=[];
  document.querySelectorAll('body *').forEach((el) => {
    if (![...el.childNodes].some(n=>n.nodeType===3&&n.textContent.trim())) return;
    const s=getComputedStyle(el), bg=bgOf(el), a=opOf(el);
    const fg=a<1?mix(s.color,bg,a):s.color;
    const L1=lum(fg), L2=lum(bg);
    const ratio=(Math.max(L1,L2)+0.05)/(Math.min(L1,L2)+0.05);
    const px=parseFloat(s.fontSize);
    const large=px>=24||(px>=18.66&&+s.fontWeight>=700);
    if (ratio<(large?3:4.5)) out.push({ text: el.textContent.trim().slice(0,32),
      ratio:+ratio.toFixed(2), px, opacity:+a.toFixed(2), need: large?3:4.5 });
  });
  return out;
})();

// C. Horizontal overflow at 375px. `over` must be 0.
({ over: document.documentElement.scrollWidth - document.documentElement.clientWidth });

// D. Which element overflows, if C is non-zero.
[...document.querySelectorAll('*')]
  .filter((el) => el.getBoundingClientRect().right > document.documentElement.clientWidth + 1)
  .map((el) => el.tagName + '.' + el.className);

// E. Controls with no accessible name.
[...document.querySelectorAll('a,button,input,select,textarea,[tabindex]')]
  .map((el) => ({ el: el.tagName,
    name: (el.getAttribute('aria-label') || el.textContent || el.placeholder || '').trim() }))
  .filter((r) => !r.name);
```

And the three commands that must pass:

```
npx tsc --noEmit
npx eslint src/
npx vitest run     # 54 tests
```
