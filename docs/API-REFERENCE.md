# API reference

All endpoints are under `/api`. Auth column: **Public** (no token),
**JWT** (any authenticated user), **Owner** (business owner role), or
**Agent** (delivery agent role). Full request/response shapes are in the
Swagger UI at `/swagger-ui.html` when the app is running.

Verified against the real controllers on 2026-09-29 (46 endpoints total,
up from a stale 38-row count — see `docs/DRIFT-LEDGER.md` for the phases
this affected).

| # | Method | Endpoint | Description | Auth |
|---|--------|----------|-------------|------|
| 1 | POST | `/api/auth/register` | Initiate registration with OTP email verification (sends a code, does not create the account yet) | Public |
| 2 | POST | `/api/auth/verify-registration-otp` | Verify the registration OTP and complete account creation (issues tokens) | Public |
| 3 | POST | `/api/auth/login` | Login with email and password | Public |
| 4 | POST | `/api/auth/refresh` | Refresh access token using refresh token | Public |
| 5 | POST | `/api/auth/logout` | Revoke all refresh tokens for the current user | JWT |
| 6 | POST | `/api/auth/forgot-password` | Request a password-reset OTP email (never reveals whether the address exists) | Public |
| 7 | POST | `/api/auth/verify-reset-otp` | Verify the password-reset OTP and receive a short-lived reset session token (JWT, 5-min TTL) | Public |
| 8 | POST | `/api/auth/reset-password` | Redeem the reset session token and set a new password | Public |
| 9 | POST | `/api/auth/resend-otp` | Resend a new OTP code (registration or password-reset), invalidating any previous active code | Public |
| 10 | GET | `/api/auth/me` | Get current authenticated user profile | JWT |
| 11 | GET | `/api/auth/invite/{token}` | Preview an agent invite before it is accepted (does not spend it) | Public |
| 12 | POST | `/api/auth/accept-invite` | Spend an agent invite and set the agent's password | Public |
| 13 | PATCH | `/api/auth/me` | Update own profile (name/phone; business name if owner) | JWT |
| 14 | POST | `/api/agents` | Create delivery agent | Owner |
| 15 | GET | `/api/agents` | List agents (filterable by `active`) | Owner |
| 16 | GET | `/api/agents/{id}` | Agent detail + stats | Owner |
| 17 | PUT | `/api/agents/{id}` | Update agent | Owner |
| 18 | POST | `/api/agents/{id}/deactivate` | Soft-delete (deactivate) agent | Owner |
| 19 | POST | `/api/agents/{id}/reactivate` | Reactivate a previously deactivated agent | Owner |
| 20 | POST | `/api/agents/{id}/invite` | Issue (or re-issue) an invite link letting the agent set their own password | Owner |
| 21 | POST | `/api/shipments` | Create shipment (auto-assign) | Owner |
| 22 | GET | `/api/shipments` | List shipments (filtered) | Owner |
| 23 | GET | `/api/shipments/mine` | Agent's assigned shipments | Agent |
| 24 | GET | `/api/shipments/{id}` | Shipment detail | Owner + Agent |
| 25 | POST | `/api/shipments/{id}/pickup` | Agent action: confirm pickup (ASSIGNED → PICKED_UP) | Agent |
| 26 | POST | `/api/shipments/{id}/start-transit` | Agent action: start transit (PICKED_UP → IN_TRANSIT) | Agent |
| 27 | POST | `/api/shipments/{id}/out-for-delivery` | Agent action: out for delivery (IN_TRANSIT → OUT_FOR_DELIVERY) | Agent |
| 28 | POST | `/api/shipments/{id}/deliver` | Agent action: mark delivered (terminal) | Agent |
| 29 | POST | `/api/shipments/{id}/return` | Agent action: mark returned (terminal) | Agent |
| 30 | POST | `/api/shipments/{id}/fail` | Agent action: log a failed delivery attempt (OUT_FOR_DELIVERY → FAILED) | Agent |
| 31 | POST | `/api/shipments/{id}/reassign` | Owner: reassign the agent on any non-terminal shipment; also un-fails a FAILED shipment back to ASSIGNED | Owner |
| 32 | POST | `/api/shipments/{id}/cancel` | Owner cancels a shipment outright from any non-terminal status | Owner |
| 33 | DELETE | `/api/shipments/{id}` | Owner permanently deletes a CANCELLED shipment, its history, and its tracking link | Owner |
| 34 | POST | `/api/shipments/{shipmentId}/attempt` | Record a delivery attempt for a shipment | Agent |
| 35 | GET | `/api/shipments/{shipmentId}/attempts` | List delivery attempts for a shipment | Owner + Agent |
| 36 | GET | `/api/track/{token}` | Public tracking by opaque token | Public |
| 37 | GET | `/api/analytics/overview` | Business-wide metrics | Owner |
| 38 | GET | `/api/analytics/agents` | Per-agent performance | Owner |
| 39 | GET | `/api/analytics/shipments/trend` | Daily volume trend | Owner |
| 40 | GET | `/api/reports/overview` | Overview report shaped for the admin overview page (`range` days) | Owner |
| 41 | GET | `/api/reports/trend` | Daily volume trend shaped for the admin trend page (`days`) | Owner |
| 42 | GET | `/api/reports/agent-performance` | Per-agent performance rows shaped for the admin page (`range` days) | Owner |
| 43 | GET | `/api/reports/register` | Filterable shipment register list for the admin register page | Owner |
| 44 | GET | `/api/reports/register/{token}` | Single-shipment inspect view by tracking token | Owner |
| 45 | GET | `/api/reports/{kind}/export` | CSV export of `agent-performance` or `register` | Owner |
| 46 | GET | `/api/health` | Fixed-path liveness probe (independent of Actuator) | Public |

Note: `POST /api/shipments/{id}/cancel` requires a `422` guard against a
shipment already in a terminal status (`DELIVERED`, `RETURNED`, or
`CANCELLED`); `DELETE /api/shipments/{id}` requires the shipment to
currently be `CANCELLED` (`422` otherwise).

## Example curl commands

```bash
# Initiate registration (sends an OTP email, does not create the account yet)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "businessName": "QuickDeliver",
    "email": "owner@quickdeliver.com",
    "password": "SecurePass123!",
    "ownerName": "Rahul Sharma",
    "phone": "+91-9876543210"
  }'

# Verify the OTP to actually create the account (check logs for the code
# when SMTP_HOST is unset — see below)
curl -X POST http://localhost:8080/api/auth/verify-registration-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "owner@quickdeliver.com",
    "code": "123456"
  }'
# Response includes accessToken/refreshToken — account now exists

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "owner@quickdeliver.com",
    "password": "SecurePass123!"
  }'
# Save the accessToken from the response

# Create a shipment (replace <TOKEN> with your accessToken)
curl -X POST http://localhost:8080/api/shipments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "customerName": "Priya Patel",
    "customerPhone": "+91-9000000001",
    "deliveryAddress": "42 MG Road, Bangalore 560001"
  }'
# Note the trackingToken from the response

# Public tracking (no auth required)
curl http://localhost:8080/api/track/<TRACKING_TOKEN>
```

## Environment variables

The application reads the following environment variables (see
`.env.example` for defaults and comments):

| Variable | Required | Default | Notes |
|----------|----------|---------|-------|
| `DB_URL` | Yes | — | JDBC connection URL, e.g. `jdbc:mysql://localhost:3306/hyperlocal_delivery?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DB_USER` | Yes | — | MySQL username (e.g. `root` for local dev) |
| `DB_PASSWORD` | Yes | — | MySQL password for `DB_USER` |
| `JWT_SECRET` | Yes | — | HS256 signing secret — must be at least 32 bytes. Generate with: `openssl rand -base64 32` |
| `CORS_ORIGINS` | No (dev), Yes (prod) | `*` (dev only — prod has no default) | Comma-separated allowed origins, e.g. `http://localhost:3000` |
| `SPRING_PROFILES_ACTIVE` | No | `dev` | Spring profile. Use `prod` on Railway (enables production logging, higher JWT access-TTL, mail, etc.) |
| `PORT` | No | `8080` | Server port. Railway/Heroku assign this dynamically — the app binds Tomcat to it via `server.port: ${PORT:8080}` in the prod profile |
| `RESET_LINK_BASE_URL` | Yes (prod only) | `http://localhost:5173/reset-password` (dev) | Base URL of the frontend's reset-password page (config key only — the current OTP-based reset flow does not send a link; kept for the `PasswordResetMailer`/`MailLinkBuilder` classes that still exist but are not called from `AuthService`) |
| `RESET_TOKEN_TTL_MINUTES` | No | `30` | Bound to the same unused reset-link path as `RESET_LINK_BASE_URL` above — the real reset session token's TTL is a separate, hardcoded 5 minutes in `AuthService.verifyResetOtp`/`JwtUtil.signResetToken` |
| `INVITE_LINK_BASE_URL` | Yes (prod only) | `http://localhost:5173/agent-setup` (dev) | Base URL of the frontend page where an invited agent sets their first password |
| `INVITE_TTL_HOURS` | No | `168` (one week) | Agent invite link lifetime in hours |
| `SMTP_HOST` | No | — | SMTP server hostname, shared by every mail sender (OTP emails, agent invites). **Leave blank to fall back to console logging (recommended for local development).** |
| `SMTP_PORT` | No | `587` | SMTP port. Ignored if `SMTP_HOST` is blank. |
| `SMTP_USERNAME` | No | — | SMTP auth username. Ignored if `SMTP_HOST` is blank. |
| `SMTP_PASSWORD` | No | — | SMTP auth password. Ignored if `SMTP_HOST` is blank. |
| `MAIL_FROM` | No | `${SMTP_USERNAME}` | Overrides the `From:` address if it should differ from the SMTP login |

For local development, leave `SMTP_HOST` blank and check the application
logs for OTP codes and invite links instead.

**Note on the two `RESET_*` env vars above:** they're real, bound config
keys (`app.mail.reset-link-base-url`/`reset-token-ttl-minutes` in
`application.yml`), but a fresh read of `AuthService.java` (2026-09-29)
found its `PasswordResetMailer mailer` field is injected and never called
— the password-reset flow was rebuilt around OTP + a short-lived JWT
reset-session token (`forgotPassword` → `verifyResetOtp` →
`resetPassword`) without removing the now-unused link-based config and
mailer classes. Documented here for accuracy, not as a recommendation to
keep relying on them; flagging in case a docs-writing session wants to
treat this as a stop-and-ask code question rather than a doc fact to
silently repeat.
