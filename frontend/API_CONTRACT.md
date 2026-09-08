# Hyperlocal frontend → Spring Boot API contract (verified and implemented)

This contract documents the real, verified endpoints in the Spring Boot backend,
read off the controllers and DTOs under `src/main/java/com/hyperlocal/delivery/`.
`src/api/*.ts` is the single place every call is made, and `src/types/api.ts`
declares every shape, derived from the same Java records.

**Where this file and the code disagree, the code wins.** `src/types/api.ts`
carries the per-field detail (nullability, the alias fields, the wire values of
the `@JsonValue` enums); this file is the map, not the territory.

This alignment delivers the design intent: explicit per-action status endpoints
(no generic status PUT), no `CREATED` state (shipments are auto-assigned straight
into `ASSIGNED`), the agent-auth check enforced on every action endpoint, and a
`reassign` endpoint that works on any non-terminal shipment (not just `Failed`).

## Wire enums — not the Java constant names

Three enums carry `@JsonValue`, so what crosses the wire is not the Java name:

- `ShipmentStatus` → lowercase: `assigned`, `picked_up`, `in_transit`,
  `out_for_delivery`, `delivered`, `failed`, `returned`. No `created`.
- `UserRole` → `OWNER` | `AGENT` (Java: `BUSINESS_OWNER`, `DELIVERY_AGENT`).
- `FailureReason` → the human label: `Customer absent`, `Address not found`,
  `Refused`, `Damaged`, `Other`.

`OtpPurpose` has no `@JsonValue`, so it does send its names: `REGISTRATION`,
`PASSWORD_RESET`.

## Envelopes and paging

Every endpoint answers inside an envelope: `{status, data}` for a single
payload, `{status, data, pagination}` for a list, `{status, code, message, …}`
for an error. `apiFetch` unwraps to `data`; `apiFetchPage` keeps `pagination`.

**Every list endpoint is paged, and nothing configures a page size**, so
Spring's default of 20 applies (`/reports/register` hardcodes 20 of its own).
`fetchAllPages` in `src/lib/apiClient.ts` walks to the end for the screens that
render a whole list — `listShipments`, `listMyShipments`, `listAgents` — and
`getRegister` loops until it gets a short page. Without that, five screens
showed their first 20 rows and gave no sign the rest existed.

## Auth
- `POST /api/auth/login` `{email,password}` → `AuthResponse`: `{accessToken, refreshToken, expiresIn, token, role, user}`. `token` is an alias of `accessToken`; the session layer reads `token`.
- `POST /api/auth/register` `{businessName,ownerName,email,phone,password}` → `{message,email}`. **Creates nothing yet** — it stores a pending registration and mails an OTP.
- `POST /api/auth/verify-registration-otp` `{email,code}` → `AuthResponse`. This is the call that creates the business + owner and returns the tokens.
- `POST /api/auth/resend-otp` `{email,purpose}` → `{message,email}`; 429 when rate-limited.
- `POST /api/auth/forgot-password` `{email}` — 202, **no body**, and the same answer whether or not the account exists (no enumeration).
- `POST /api/auth/verify-reset-otp` `{email,code}` → `{resetToken}`.
- `POST /api/auth/reset-password` `{resetToken,newPassword}` — 204. (`token`/`password` also accepted via `@JsonAlias`.)
- `POST /api/auth/refresh` `{refreshToken}` → `{accessToken, refreshToken, expiresIn}`.
- `POST /api/auth/logout` — 204, revokes every refresh token for the user.
- `GET /api/auth/me` → `UserResponse`.
- `PATCH /api/auth/me` `{fullName?,phone?,businessName?,businessPhone?}` → updated `UserResponse`.

A rejected OTP answers with `OtpErrorResponse` — `{error, reason, attemptsRemaining}` — which both OTP surfaces read to decide whether a retry is still allowed.

## Shipments
- `GET /api/shipments?status=&agentId=&from=&to=&page=&size=` — owner register. Paged; returns `ShipmentSummaryDto` rows (thinner than the detail shape: no phone, no events, no attempts).
- `GET /api/shipments/mine?status=&page=&size=` — the agent's own queue, same row shape.
- `GET /api/shipments/:id` → `ShipmentResponseDto`, the full detail with `events` and `attempts`.
- `GET /api/track/:token` — **public, no auth.** Shaped server-side to the privacy boundary: business name and phone, status, customer name, address, scheduled time, delivered-at, and `events` (a `{status,label,stamp}` timeline) only. Never agent identity, agent phone, internal ids, or failure reason.
- `POST /api/shipments` `{customerName,customerPhone,address,scheduledAt}` — owner create; server auto-assigns to the least-loaded active agent and starts at `ASSIGNED`. `address` and `scheduledAt` are `@JsonAlias`es for the Java `deliveryAddress`/`scheduledDeliveryAt`; `scheduledAt` must be in the future.
- `POST /api/shipments/:id/pickup` `{note?}`
- `POST /api/shipments/:id/start-transit` `{note?}`
- `POST /api/shipments/:id/out-for-delivery` `{note?}`
- `POST /api/shipments/:id/deliver` `{note?}`
- `POST /api/shipments/:id/fail` `{reason,notes?}` — appends to the immutable attempt log; enforce append-only server-side.
- `POST /api/shipments/:id/return` `{note?}`
- `POST /api/shipments/:id/reassign` `{agentId?,note?}` — owner only. `agentId` omitted or null = auto (least-loaded active agent). Legal on any non-terminal shipment. If the shipment is `FAILED`, the same call also returns it to `ASSIGNED` — the one status transition an owner may perform.

Every per-action endpoint above verifies that the authenticated agent is the one
currently assigned to that shipment (the bug this alignment fixed was this check
being dead code).

## Agents
- `GET /api/agents?active=&page=&size=` — paged, and **not the same shape as the single-agent endpoints**: rows are `AgentSummaryDto` (`id, name, email, phone, active, openCount, createdAt`) with no `deliveredCount`, `failedCount` or `updatedAt`, and the join timestamp is `createdAt`, not `joinedAt`.
- `GET /api/agents/:id` → `AgentResponse` (`…, deliveredCount, failedCount, joinedAt, updatedAt`).
- `POST /api/agents` `{name,email,phone}` → `AgentResponse`. `name` aliases `fullName`; omitting `password` means "generate one server-side".
- `PUT /api/agents/:id` `{name,phone}` — email is the login, never editable here.
- `POST /api/agents/:id/deactivate` / `POST /api/agents/:id/reactivate` → `AgentResponse`. Soft delete; history and past shipments stay intact.

## Reporting (owner-only, entirely read-only)
- `GET /api/reports/overview?range=7|30|90` → `OverviewReportDto`
- `GET /api/reports/trend?days=` → `TrendReportDto` (`rows` and `days` are the same list)
- `GET /api/reports/agent-performance` → `AgentPerformanceRowDto[]`
- `GET /api/reports/register?status=&agentId=&from=&to=&page=` → `RegisterRowDto[]`. **`status` here takes a list**: the param is a String run through `parseStatuses`, which splits on commas and 400s on an unknown value — which is why the register's filter can offer `delivered,failed` as one option while `/shipments` takes a single status. 20 rows per page, and the response is a bare list with no pagination block.
- `GET /api/reports/register/:token` — inspect, read-only
- `GET /api/reports/:kind/export` — raw CSV, not the JSON envelope; `kind` = `agent-performance` | `register`

None of these ever write. Every actionable link in the admin UI routes back
into `/owner/*`.

## Deliberately not wired up
`OwnerAccountPage` is **read-only by design**. `PATCH /api/auth/me` does now
exist, so the constraint is a product decision rather than a missing endpoint —
don't add a save action there without asking.

The frontend never calls `/api/analytics/*` (`overview`, `agents`,
`shipments/trend`) or the `/api/shipments/:id/attempt(s)` pair; the reporting
screens use `/api/reports/*` and the attempt log arrives inside the shipment
detail.
