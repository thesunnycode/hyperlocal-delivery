# Redesign notes

Applied against the source in this folder. Pass two of the brief.

---

## 1. The direction

The console got **quieter in structure and louder in signal**. A shop owner
opens this app to answer one question — *what needs me right now* — and the old
console answered it with a 216px sidebar, a page title, and a queue you had to
read. The redesign spends its structure on that question instead: the sidebar
is gone, navigation is a 56px top bar, and each page's own shape is the thing
you recognise when you land on it.

Visually it is a **cool off-white page, true-white cards, and one saturated
emerald** that means *this worked, or do this*. The seven statuses get real,
separated hues — slate, cobalt, orange, emerald, red, violet — rationed to the
rows that have earned them, so nothing important reads as grey and nothing
unimportant reads as coloured. Type is **Bricolage Grotesque** for display,
**Instrument Sans** for UI, **JetBrains Mono** for anything read digit by digit.

### What I rejected

- **The sidebar.** It was the shape of the old app and the reason every screen
  looked identical. Defensible for a product with many modules; this one has six
  destinations and will not grow.
- **The warm neutral ramp** (`#eeefe9`, `#f7f8f4`). Technically harmonious,
  visually flat next to any saturated accent — it read as dusty. Same
  lightnesses, cool axis.
- **Instrument Serif.** Charming on the auth page, mush at 19px on a rider's
  phone in sunlight. The character moved into a tightly tracked grotesque.
- **A blue for in-transit.** It collided with the brand hue at a glance, so
  every moving shipment looked like a button. Cobalt now sits opposite emerald.
- **A component library.** Forbidden by §3.5, and unnecessary: the existing CSS
  is well-scoped and already token-driven.
- **Dark mode as a shipped toggle.** Specified and measured (see §3), not wired.
  Reason in §7.

---

## 2. Architecture — one token layer

The brief (§3.6) asks for shared tokens in **one** place. Two new files:

| File | Role |
|---|---|
| `src/styles/tokens.css` | The only place a colour, a font, a radius, an elevation or a duration is declared. Imported **first**. |
| `src/styles/scopes.css` | Re-points each scope's private tokens (`--ow-*`, `--ag-*`, `--tk-*`, `--auth-*`, `--rp-*`, `--es-*`, `--ov-*`) at the shared roles. Imported **last**. |

**Why the bridge is a separate file imported last.** Each scoped sheet opens
with a block of literal hexes and then uses `var(--ow-ink)` for ~2,300 lines
below. That indirection is the redesign's best seam: repoint the declarations
and the whole app re-skins without a single layout rule being touched — which is
also why this revamp puts no regression anywhere near the 54 passing tests.
Custom-property declarations at equal specificity resolve last-wins, so
`scopes.css` overrides the literals still sitting at the top of the six sheets.

Those literal blocks are now dead. They are **left in place deliberately** so
the diff stays reviewable and `git revert` of two files restores the old look
exactly. Deleting them is listed in §7.

`base.css` was rewritten to consume the tokens rather than carry its own
literals — it previously had a comment explaining there was no token sheet to
read from. There is one now.

---

## 3. The token system

### Neutral ramp — cool slate

| Token | Hex | Job |
|---|---|---|
| `--hl-n0` | `#ffffff` | raised surface — cards, rows, sheets |
| `--hl-n25` | `#f7f9fb` | page background |
| `--hl-n50` | `#f1f5f9` | sunken — table heads, hover, inert wells |
| `--hl-n100` | `#eef2f7` | sunken, one step down — footers |
| `--hl-n200` | `#e4e9f0` | hairline border (decorative; no floor applies) |
| `--hl-n300` | `#cfd8e4` | control border — inputs, secondary buttons |
| `--hl-n400` | `#808c9e` | strong / meaningful border |
| `--hl-n600` | `#5e6a7a` | tertiary text |
| `--hl-n700` | `#45505f` | secondary text |
| `--hl-n900` | `#0f172a` | primary text |

### Brand — three values, one hue

| Token | Hex | Job |
|---|---|---|
| `--hl-brand` | `#00875f` | identity hue: borders, dots, chart fills, focus |
| `--hl-brand-solid` | `#007a56` | fills carrying **white text** |
| `--hl-brand-deep` | `#046a4e` | hover / press on any brand surface |
| `--hl-brand-soft` | `#d4f7e7` | tinted well, active nav, selected row |
| `--hl-brand-ring` | `#bdf3dc` | 3px focus halo |

`brand` and `brand-solid` are split because `#00875f` measures **4.53:1** with
white — it passes AA by 0.03, and anything that tight breaks the next time
someone nudges it. `#007a56` is 5.36:1 and indistinguishable at a glance.

### Status — seven keys, four values each

Keys are unchanged and still owned by `src/utils/statusMachine.ts`.

| Status | solid | tint bg | tint ink | white-on-solid |
|---|---|---|---|---|
| `assigned` | `#4e5a6b` | `#f1f5f9` | `#4e5a6b` | 7.00:1 |
| `picked_up` | `#1d4ed8` | `#e0f2fe` | `#1d4ed8` | 6.70:1 |
| `in_transit` | `#1d4ed8` | `#dbeafe` | `#1d4ed8` | 6.70:1 |
| `out_for_delivery` | `#b34700` | `#ffeedb` | `#b34700` | 5.50:1 |
| `delivered` | `#007a56` | `#d4f7e7` | `#046a4e` | 5.36:1 |
| `failed` | `#c81e1e` | `#ffe7e5` | `#c81e1e` | 5.74:1 |
| `returned` | `#6d28d9` | `#f0e9fe` | `#6d28d9` | 7.10:1 |

The two in-flight states deliberately **share one cobalt at two depths**, so a
journey reads as one movement rather than two events. Colour is never the only
carrier: every badge keeps its label and its lucide icon.

### Contrast — measured

Text pairs (floor 4.5:1, or 3:1 at ≥24px / ≥18.66px bold):

| Pair | on `n0` | on `n25` | on `n50`/`n100` |
|---|---|---|---|
| `n900` primary | 17.85 | 16.92 | 15.88 |
| `n700` secondary | 8.18 | 7.76 | 7.28 |
| `n600` tertiary | 5.50 | 5.21 | 4.89 |

| Pair | Ratio |
|---|---|
| white on `brand-solid` | 5.36 |
| white on `brand-deep` | 6.61 |
| `brand-deep` on `brand-soft` | 5.75 |
| `danger-ink #8c1c16` on `danger-bg #ffe7e5` | 7.77 |
| `warn-ink #9a3412` on `warn-bg #fff7ed` | 6.88 |
| `#ffb4a0` on the rider's ink strip | 10.45 |
| auth panel kickers `#a4a7ae` on `n900` | 7.41 |

Non-text (floor 3:1): `n400 #808c9e` on `n0` = **3.41**. This is the value used
for the strong border, the sort-arrow hint and any meaningful edge. `n200` and
`n300` are decorative dividers and carry no floor.

`n600` was originally `#647082`, which measured **4.47** on the sunken surfaces
— three hundredths short. Darkened to `#5e6a7a`.

### Type

| Face | Job |
|---|---|
| Bricolage Grotesque (600, 700) | display — headlines, figures |
| Instrument Sans (400–700) | all UI and body copy |
| JetBrains Mono (500, 600) | ₹ amounts, +91 numbers, tokens, timestamps, table figures |

Scale, as declared in `tokens.css`:

| Token | Spec |
|---|---|
| `display-1` | 700 · 34/1.12 · −0.03em |
| `display-2` | 700 · 27/1.16 |
| `title-1` | 600 · 21/1.25 |
| `title-2` | 600 · 17/1.3 |
| `body-1` | 400 · 14.5/1.6 |
| `body-2` | 400 · 13/1.55 |
| `label` | 600 · 13/1.4 |
| `kicker` | 600 · 10.5 · +0.09em caps |
| `mono-data` | 500 · 13/1.4 tabular |

**Mono is a deliberate reversal of a documented decision.** `index.html`
previously said "Mono is deliberately the system stack: no second face." The
system stack renders at a different width on every machine and this product's
tables are full of numbers that must line up. Called out here because it
overturns a comment that argued the other way.

Repointing a font token swaps the face but keeps the old face's metrics —
Instrument Serif was set at weight 400 everywhere because it had no other
weight, and Bricolage at 400 reads as a draft. `scopes.css` restates weight,
size and tracking for every display slot.

### Spacing, radius, elevation

- **Spacing:** 4px base — 4, 8, 12, 16, 20, 24, 32, 40.
- **Radius:** 7 (chips) · 10 (controls) · 14 (cards) · 18 (sheets) · pill. The
  six sheets had drifted to **eleven** different radii; `scopes.css` pulls the
  outliers onto the five-step scale.
- **Elevation:** `e1` cards · `e2` popovers and menus · `e3` sheets and modals ·
  plus a shared scrim. Cool-tinted to match the ramp.

### Motion

One easing family. In: `cubic-bezier(.2,.7,.3,1)`. Out: `cubic-bezier(.4,0,1,1)`
— always faster leaving than arriving.

| Token | Duration | Used for |
|---|---|---|
| `--hl-d-press` | 120ms | button press, chip toggle, nav tint |
| `--hl-d-hover` | 140ms | row tint, border darkening, focus ring |
| `--hl-d-enter` | 200ms | skeleton→content, panel open, inline alert |
| `--hl-d-sheet` | 240ms | sheet/modal translate, **status advance** |
| `--hl-d-exit` | 140ms | everything leaving |

`prefers-reduced-motion` collapses all five duration tokens to `1ms` **at the
token layer**, so every transition built from them is neutralised without any
rule needing to know. The keyframe animations keep their explicit guards too.

---

## 4. Per-screen changes

Every screen inherits the palette, the type pairing and the motion layer. Listed
below is what changed *beyond* the re-skin.

| # | Screen | Change |
|---|---|---|
| 1–4 | Auth | Panel is now the ink neutral rather than a mid-navy that fought the emerald — it becomes the darkest thing in the product, which gives the form the contrast to be the focus. The two tracked-out kickers were set with `opacity`; now explicit at 7.41:1. Reveal button gets a 44px touch target under 480px. |
| 5 | Customer tracking | The `opacity:.9` on the illustration is removed — a composite nobody chose. The offline notice moves onto the shared warn tokens (6.88:1). Rail segments and nodes animate on `--hl-d-sheet`, so a status advancing reads as an advance. |
| 6–7 | Shipments + record | **Sidebar → top bar** (see §5). The queue gets its 216px back. Detail pane arrives on a 14px slide-and-fade; the list never moves. |
| 8–9 | Create / reassign | Overlay radii and shadows onto the scale; scrim onto the shared token. |
| 10 | Register | Active-sort column tint moves from a pale old-brand blue to the brand's own tint, so "this is the column you sorted by" is the same signal as every other active state. |
| 11 | Riders | Row hover, kebab and menu onto the cool ramp; menu shadow onto `e2`. |
| 12 | Invite sheet | Sheet radius and `e3`. |
| 13 | Owner account | Danger card and its footer onto the shared danger tokens (7.77:1). |
| 14–16 | Reports | **Sort arrows were `opacity: 0` until hover** — fixed, see §6. KPI and card fades on `--hl-d-enter`. Chart series keep the small-multiples split, now drawing the product's own status hues rather than a separate ramp. |
| 17–19 | Rider app | Dark summary strip is the ink neutral; its "hot" figure re-measured at 10.45:1. The log-attempt button's `opacity: 0.4` disabled style is replaced with explicit colours. Chips, icon buttons and the sheet close get 44px targets under 480px. |
| 20–21 | Edge states | Onto shared tokens; buttons join the press-feedback layer. |
| — | Rider illustration | Rebuilt — see §4b. |
| — | Toasts | The destructive toast was the only surface using a maroon that appears nowhere else. Now on the danger tokens at 7.77:1. |

### 4b. The rider illustration

`components/RiderIllustration.tsx` and `styles/rider.css`. It was structurally
sound and flat: every shape a single fill, so the scooter read as a decal, and
the rider had one arm and one leg, which made the pose ambiguous at the mobile
crop.

**Drawing**

- The vehicle body is now **one closed silhouette** — rear haunch, deck,
  legshield — instead of three overlapping masses. A viewer resolves a vehicle
  by its outline before any of its parts; three individually-correct slabs read
  as unrelated grey shapes. The step-through notch cut into the top edge is the
  single feature that makes it a scooter rather than a motorcycle.
- **Layer order fixed.** Wheels now draw *behind* the body. Painted last, the
  44px wheels were erasing the deck and legshield entirely, which is why the
  first attempt rendered as two wheels, a seat, and nothing joining them.
- Two-layer shading throughout, lit from up-and-left. A far-side arm and leg at
  50% so the rider sits **on** the scooter rather than beside it.
- A **hi-vis band** on the jacket — the one detail that makes the figure read
  as a courier rather than a commuter.
- Wheels gained a tread ring, hub, rim lip and mudguards. The mudguards sit
  outside the spinning group; inside it they rotate with the spokes and the
  wheel reads as a spinning disc.
- Soft radial ground contact instead of a flat ellipse the scooter sat on top of.

**Three defects the first pass introduced, and their fixes**

1. **Gradient stops using `var()`** fell back to black in testing, putting a
   hard triangle where the headlight beam was. Gradients now use flat stops;
   surface-dependent colour lives on the shapes, where it always resolves.
2. **The headlight beam is gone.** A flat gradient wedge never reads as light —
   it was an opaque tan triangle on the dark panel and had to be hidden entirely
   on a light page, so the drawing was inconsistent between its two homes. The
   lamp lens alone carries the idea and works on both.
3. **The mirror floated free** as a white egg on a long stalk. Shortened and
   rooted in the bar. The **exhaust bisected the rear wheel** — moved below the
   axle. The **hand hovered beside the grip** — now overlapping it.

**Palette**

- Derives from `tokens.css`. The scooter was `#2c437b`, a navy chosen for the
  old navy auth panel; against emerald a blue vehicle competed with the primary
  action for the eye. It is slate now, leaving the hi-vis jacket and the status
  parcels as the only saturated things in frame — the courier and the cargo are
  the subject.
- Vehicle greys, tyres and rims are **surface-aware**. One mid-slate cannot
  separate from both a `#0f172a` panel and a `#f7f9fb` page; on the dark one
  the whole bike sank and the drawing read as a rider floating between two
  dark holes.

**New `status` prop**

Tints the three floating parcels to the shipment's real state. The tracking page
knew the status and threw it away here, so a delivered order had an orange
out-for-delivery parcel drifting beside the word "Delivered". Omitted, the
parcels show the neutral in-flight trio, which is right for the auth panel where
there is no shipment. Typed against `ShipmentStatus` from `types/api.ts`, so it
cannot drift from the seven keys.

`still` now also hides the speed streaks — a parked bike has no slipstream, and
leaving them was the one thing that gave the state away as a paused animation
rather than a stopped scooter.

**Ceiling.** This is hand-authored vector art. It is materially better than it
was, but a commissioned illustration will beat it; if the auth panel and the
tracking page matter commercially, brief an illustrator against these tokens.

---

## 5. The one structural change

`layouts/OwnerLayout.tsx` renders a **56px top bar** instead of a 216px
sidebar. `.ow-side` is no longer rendered; `.ow-app` is now rows, not columns.

Everything the sidebar did still exists: both nav landmarks with their distinct
accessible names (`Operations`, `Reporting`), the account button that opens the
account page rather than signing out on one click, and the mobile tab bar as its
own element. Register stays desktop-only. Below 899px the bar keeps only the
brand and the account button, because repeating six links in a sideways-scrolling
row when the tab bar already has them is two navigations for one job.

The bar's *content* obeys `--ow-gutter`, so the brand and the account button
line up with the page furniture below them — a full-bleed bar whose content runs
to the window edge is the same asymmetry defect the brief documents, rotated.

**Nothing was dropped.** No control that existed before is gone.

---

## 6. Audit findings fixed

The brief (§2) lists failure modes to design away from. Three had survived into
the current code:

1. **"Nothing important behind hover alone."** `reports.css` had
   `.rp-sort .ar { opacity: 0 }` — on a touch screen the table gave no signal
   its columns were sortable. `owner.css` had already fixed exactly this on
   `.ow-sort` and left a comment about it; reports never got the same treatment.
   Now visible at `n400` (3.41:1), brand-coloured when active.
2. **"`opacity` as a disabled style."** `.ag-sheet-cta button[disabled]` was at
   `opacity: 0.4` — the most consequential button in the field app. Explicit
   colours now, 5.02:1. Same for `.ag-chip em`.
3. **Touch targets under 44px** on the two phone-first surfaces — the rider's
   sheet close and icon buttons, the tracking page's refresh, the auth reveal
   button. All raised under 480px.

---

## 7. Invariants, tests, and what I did not do

### §3 invariants — nothing changed

- **API:** untouched. `apiClient.ts` not edited; no `fetch` added anywhere; the
  envelope, `OFFLINE_MESSAGE` and every export are as they were.
- **Storage keys and roles:** untouched.
- **Routes:** untouched, including the `/admin/*` and `/owner/login` redirects.
  `OwnerLayout` changed its *markup*, not its route or its `Outlet`.
- **Status machine:** untouched. Seven keys, same order, same rules.
- **Build:** `build.outDir` untouched; no dependency added — the motion layer is
  CSS, so §3.5's "add a JS animation dependency only if it earns its place" did
  not come up. No Tailwind, no component library, no CSS-in-JS.
- **Stylesheet architecture:** all six scopes keep their prefixes and their
  files. Two files were added, which is what §3.6's "shared tokens in one place"
  requires.

### Tests

**No test was modified.** The 54 tests cover `apiClient`, `reportsApi`,
`format` and `statusMachine` — no component or snapshot tests exist, which is
why a structural change to `OwnerLayout` carries no test risk.

### Not done, and why

- **Dark mode is specified but not wired.** The full palette is in
  `tokens.css` under `[data-theme='dark']` — twelve roles and all seven statuses
  re-derived against a near-black page, measured (n900 15.11, n700 8.33, n600
  5.48, brand 9.13, danger 7.99), with `/track/:token` opting back out because
  the shop window should look the same to every recipient. What is missing is
  the toggle and a pass over the ~40 places where a scoped sheet hardcodes
  `#fff` for text on a coloured fill — correct in light, wrong in dark. §2 is
  explicit that a half-converted dark mode is worse than none, so it ships off.
- **Cash on delivery is not in the API.** Every shipment in the approved mockup
  showed a ₹ amount for the rider to collect, and the rider's proof-of-delivery
  sheet recorded it. **No field in `API_CONTRACT.md` carries it.** Per §3.1 I
  have not faked it against a live backend — none of it is in this code. It
  needs a field before it can ship.
- **Proof of delivery** (door photo, cash confirmation) is designed in the
  mockup and not implemented here, for the same reason: it needs an endpoint.
- **The per-page structural work below the shell** — the answer-first
  Operations home, the record-as-overlay, bulk return-to-queue, undo toasts,
  prev/next between records, saved views, the tracking page's FAQ and SMS
  opt-in — is designed and approved in the mockup but **not applied to the
  page components**. This pass covers the token system, the type system, the
  motion layer, the audit fixes and the shell. The page bodies still render
  their previous structure, now correctly skinned. That is the honest state of
  it: not half-migrated visually, but structurally the pages are still the old
  arrangement.

### Follow-up

1. ~~Delete the dead literal token blocks at the top of `owner.css`, `auth.css`,
   `agent.css`, `track.css`, `reports.css`, `edge.css` and `overlay.css`, and
   the `.ow-side` block. They are overridden, not read.~~ Done (2026-09-11).
2. Apply the approved page-level structures listed above.
3. Add the `cashToCollect` field, or drop it from the design.
4. Wire the dark-mode toggle once the `#fff`-on-fill audit is done.

### Verification

`tsc --noEmit`, `eslint src/` and `vitest run` were **not executed** — this
environment has no shell. The changes are CSS plus one component whose imports,
props and exports are unchanged, so all three are expected to pass; please run
them before merging.

---

## 8. Page-level pass — what landed, and what did not

The first pass was the foundation: tokens, scopes, type, motion, the shell and
the illustration. This pass is the page level. It is **partial and honest about
being partial** — the list below separates what is in the code from what is
still only in the mockup, so nobody has to diff to find out.

### Applied

**`lib/ToastContext.tsx` + `components/ToastStack.tsx` — toast actions.**
`push()` gains an optional third argument, `{ label, onAct }`. The existing
two-argument signature is untouched, so every current call site and every test
that asserts on toasts is unaffected. A toast carrying an action lives 7s
instead of 3.2s, because 3.2s is enough to register "done" but not to notice a
mistake, move the mouse and click. Deliberately **one** action, not a list: a
toast with two buttons is a dialog that has escaped onto the corner of the
screen.

**`pages/owner/OwnerShipmentsPage.tsx` — three structural changes.**

1. *The page leads with its answer.* The largest type on the console was the
   word "Shipments", which nobody opens the console to read, while the thing
   they came for was a 13px sub-line and a number in a KPI cell on the far
   right. The hero now states it at `--hl-t-display-1`: **"3 deliveries need
   you"**, in the danger ink, with the consequence spelled out underneath.
   The calm case occupies the same slot — "Nothing needs you" — because a hero
   that only appears on bad days trains the owner to read its absence as "not
   loaded yet", and "all clear" is an answer that deserves stating.

2. *One page-level range.* `Today / 7 days / 30 days / All time`, and every
   figure obeys it: the counts, the chips, the KPI cells, the failed block, the
   latest block, the list. It filters the set already in memory rather than
   refetching — this console serves a shop with a handful of riders, the whole
   list is already here, and a round-trip per chip press would make the control
   feel heavier than the answer it changes. A shipment with no `scheduledAt`
   is excluded from a narrowed range rather than included, so a count never
   silently absorbs undated rows.

3. *Bulk return, with undo instead of confirm.* Three failures used to be three
   open-act-close journeys. There is now a select bar over the failed set only
   — a checkbox on every row of the register would be a data-management
   affordance on a screen whose job is triage, and there is exactly one thing
   an owner does to a failure. It reuses the single-shipment endpoint per id
   rather than inventing a batch call the API does not offer, uses
   `Promise.allSettled`, and reports a partial failure honestly ("4 returned,
   1 could not be") instead of hiding it behind one all-or-nothing result.

   Returning to the queue is **reversible** — it writes a status the shipment
   held five minutes ago — so guarding it with a dialog taxes the common case
   to protect the rare one. It fires a toast with Undo.

**`styles/scopes.css`** — new rules for `.ow-answer`, `.ow-bulk`, `.ow-box`,
`.ow-scope`, `.ow-seg`, `.ts-act`. Appended below a divider so the re-skin
layer and the new-structure layer stay separable.

**`components/EdgeStateCard.tsx` — support contact.** New `support` prop.
The card told an owner to quote a reference code without saying quote it to
WHOM: a dead end dressed as a next step.

Read from `VITE_SUPPORT_EMAIL` / `VITE_SUPPORT_PHONE` / `VITE_SUPPORT_HOURS`
with **no fallback**, on purpose — an error screen that prints an invented
address is worse than one that prints none, because the user writes to nobody
and concludes the product is broken twice. Unset, the block does not render and
the screen is exactly as it was.

Opt-in per screen rather than always on:

- **500** (`OwnerFatalScreen`) shows it. There is a reference code and no
  self-service fix.
- **404** shows it only to a **signed-out** visitor. A shop owner who mistypes
  a console URL has a working app one click away; a customer whose tracking
  link has gone stale has nowhere else to go, and telling them to "open the
  link that was sent to you" when that link is the broken one is the dead end.
- **Expired session** deliberately does not. It is fixed by signing in again,
  and a support number there invites a call nobody needs to take.

**`pages/LoginPage.tsx` — Caps Lock hint.** The one thing this screen can tell
a user that the server cannot, since the rejection deliberately refuses to say
which field was wrong. Read from the keyboard event and only while the password
field has focus — a global listener would announce it on a page with nothing to
type into. `aria-live="polite"`, not `role="alert"`: nothing has failed yet,
and interrupting a screen reader mid-word is worse than the typo it prevents.

### Four more audit findings that were wrong

Recorded for the same reason as the two below — a note that only lists wins is
not a record. In every case the mockup was behind the code, not ahead of it.

- **"Login has no show-password toggle."** It does, and has throughout
  (`.auth-reveal`). So do `RegisterPage` and `ForgotPasswordPage`.
- **"Owner has no change-password section but the rider does."** Both route to
  `/forgot-password`.
- **"Reports tables should be real `<table>` elements."** They already are.
- **"Reports have no loading or empty states."** They do; the audit was reading
  the mockup's state list, not the source.

### Two more, from the first page-level pass

- **"Owner has no change-password section but the rider does."** False. Both
  `OwnerAccountPage` and `AgentAccountPage` already route to
  `/forgot-password`. The gap existed in the mockup, not in the code. No change
  made.

- **"Reports tables should be real `<table>` elements."** They already are.
  The CSS-grid-with-ARIA-roles compromise is a limitation of the *mockup's*
  template engine, which reparents repeated rows out of table markup. It never
  applied to the React source.

### Not applied, and why

| Mockup feature | Why it is not in the code |
|---|---|
| Proof of delivery — door photo, cash-collected switch | No API field carries either. Building it would be UI that cannot save. |
| Cash to collect, rider earnings card | Same: `API_CONTRACT.md` has no amount field. |
| Register saved views, pagination | Needs a persistence decision (localStorage vs a server-side view resource) that is a product call, not a design one. |
| Tracking-page SMS opt-in | Needs a notifications endpoint and a consent record. |
| Tracking-page language toggle | Needs an i18n layer; every string in the app is currently a literal. |
| Reports KPI trend chips | Needs a previous-period comparison the reports API does not return. |
| Rider route order, queued-offline count | Needs a route-ordering field and an outbox; both are real features, not styling. |
| Record prev/next | Cheap, but it changes URL semantics for `/owner/shipments/:id` and deserves its own change. |
| Register sort indicators, filter counts | Buildable today. Not done — see "Still open" below. |
| Riders always-visible row menu, inline Resend | Buildable today. Not done. |
| Reports bar hover values, worst-day marker | Buildable today. Not done. |
| Tracking FAQ block, live-update indicator | Buildable today. Not done. |
| Register/Forgot strength meter, OTP cooldown | Buildable today. Not done. |
| Dark mode toggle | Palette is measured and in `tokens.css` under `[data-theme='dark']`. Nothing sets the attribute yet — that is a settings decision. |

Everything in that table is specified in `Hyperlocal Redesign v2.dc.html`, which
is the spec for whoever picks it up.

### Verify before merging

```
npx tsc --noEmit
npx eslint src/
npx vitest run
```

I have no shell in this environment and have not executed them. The changes are
additive at every seam I could find — the toast signature is backward
compatible, no route changed, no storage key changed, no status key changed,
and `build.outDir` is untouched — but that is reasoning, not a green run.

---

## 9. Register + roster pass

**`pages/owner/OwnerRegisterPage.tsx`**

The sort affordance the audit asked for was already complete — direction
arrows, an `on` state, a muted hint arrow on unsorted columns, real
`aria-sort`. Two real things were wrong instead:

- **Presentation living in the component.** Inline `style=` props on cells, a
  seven-element array of magic pixel widths for the skeleton, and — worst — a
  `<style>` element rendered *inside the component*, which ships a duplicate
  copy of its rules into the DOM on every mount and sits outside the cascade
  so nothing can override it. All three moved to `scopes.css`.
- **"Clear filters" gave no count.** An owner looking at a short register could
  not tell whether they were seeing a narrow slice or an empty business. It now
  reads "Clear all 3 filters" with a count chip, and the empty state says how
  many filters produced it. Search and the date pair each count once — a
  half-open range is one narrowing, not two.

**`pages/owner/OwnerAgentsPage.tsx`**

Both audit findings were wrong:

- *"Row menu invisible until hover."* It is not. `.ow-kebab` is always painted
  and only changes background on hover, which is correct for a per-row overflow
  control.
- *"'Never signed in' should offer Resend inline."* **There is no field to
  detect it with.** `AgentSummary` carries no `lastLoginAt`; `joinedAt` says
  when the account was created, not whether anyone has used it. Inferring it
  from zero counts would label a brand-new active rider as never having signed
  in. "Send sign-in link" stays in the row menu, where it works for every
  agent, until the API can tell the two apart.

What was real: six inline `style=` objects, including a detail-sheet avatar
that duplicated `.av` at a larger size with two `var()` lookups inline.

**`pages/owner/OwnerAccountPage.tsx` — sticky save bar.** Four fields tall
meant editing the business phone on a laptop viewport put Save below the fold:
the owner typed, then went hunting for the way to keep it. `position: sticky`,
not `fixed`, so it stays inside its own card and stops pinning when that card
scrolls past — a fixed bar would hang over the Email and Sign-out cards below,
which it has nothing to do with. Sticky only while dirty; a pinned bar reading
nothing is furniture. The unsaved count also gained an amber dot, because
"2 unsaved changes" in the same grey as every other label is easy to read past.

**`pages/customer/CustomerTrackingPage.tsx` — FAQ block.** This is the only
screen a stranger sees, it has no navigation, and every question it cannot
answer becomes a phone call to a shop owner who is out on a round. Four
disclosures: can I change the address, nobody will be home, how do I pay, can I
speak to the agent. Each answer names the next action, and the payment one says
the page never asks for card details — a tracking link is a phishing target and
saying so costs one clause. The business name is threaded through the copy, so
it reads "call Nandini Stores" rather than "call the store". Reuses `.tk-more`,
so it inherits the disclosure styling the privacy and history blocks already
use. Nothing here needs a field the tracking payload does not already carry.

**`components/PasswordStrength.tsx` (new) + `pages/RegisterPage.tsx`.**
"At least 8 characters" rewards `password` and `12345678` identically, and
this is the console that holds a shop's customer addresses and phone numbers.

Four levels, not five — five is a distinction nobody can act on, three cannot
separate "long but obvious" from "genuinely fine". Length outweighs character
variety, which is where the research has sat for years, and a small obvious-list
is checked *before* the variety score or `Password123!` scores as strong.

Two deliberate limits: it is **advisory only** — the server's minimum is the
control, and a client-side gate that disagreed with it would refuse a password
the API would have taken — and it renders nothing until something is typed,
because a row of empty segments reading "Too short" scolds someone who has not
started. Colour runs warning → brand on one axis; no danger red, because a
weak-but-acceptable password is not an error.

Also added there: a "check spam" line under the OTP resend. The one thing a
code screen cannot fix by itself is a code that never arrived.

### Running total of wrong audit findings: 12

Login reveal toggle, owner change-password, real `<table>` elements, reports
loading/empty states, register sort indicators, register filter counts (no
chips exist on that screen — they are `<select>`s), roster row-menu
visibility, roster never-signed-in detection, and both reports items — DayBars
already has per-bar hover values, keyboard arrow-key stepping, a peak label, a
baseline axis, an `aria-live` announcement and a visually-hidden data table.
And both OTP items: `ResendCodeButton` has always carried a 30-second cooldown,
and `OtpInput` tracks an absolute expiry that survives a resend remount. The
wrong-code state exists too, and shows the server's remaining-attempt budget.

Every one was the mockup being **behind** the code rather than ahead of it.
Worth stating plainly: the audit was written by reading a mockup I had built,
not by reading this source, and that is the wrong order.

---

## 10. Still open

Applied so far, page level: **owner shipments console**, **register**,
**roster**, **owner account**, **customer tracking**, **toasts**, **edge
states**, **login**. Everything else in `src/pages` picks up the new palette,
type scale, spacing, radii, motion and focus states through the token layer —
so it *looks* redesigned — but keeps its current structure.

Buildable against today's API, specified in the mockup, not yet written:

| Screen | What is missing |
|---|---|
| Owner account | Sticky save bar |
| Reports · overview | Per-bar hover values; baseline axis; worst-day marker |
| Reports · trend | CSV export |
| Customer tracking | FAQ block; live-update indicator on the timestamp |
| Forgot password | Strength meter — the component exists; one import and one tag per site |
| Rider setup | Same |

Blocked on API work, and listed in §8's table with reasons: proof of delivery,
cash amounts, rider earnings, KPI trend comparisons, SMS opt-in, route
ordering, i18n, dark-mode toggle.

`Hyperlocal Redesign v2.dc.html` is the spec for all of it.
