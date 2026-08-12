<h1 align="center">Hyperlocal Delivery</h1>

<p align="center">
  A delivery-management platform for local shops running deliveries over<br>
  WhatsApp and a spreadsheet — shipment tracking, rider assignment, and a<br>
  public tracking link, with none of that.
</p>

<p align="center">
  <img src="https://skillicons.dev/icons?i=java,spring,react,ts,mysql,maven,git&perline=7" alt="Java, Spring Boot, React, TypeScript, MySQL, Maven, Git" />
</p>

<br>

### 🚚 Owner console · rider app · public tracking

An owner creates a shipment and it's auto-assigned to the least-loaded
rider. The rider moves it through pickup → transit → delivered (or failed,
or returned) from a mobile-first app. The customer gets a link — no login,
no app — that updates live and never reveals who's carrying their order.

<table align="center">
<tr>
<td align="center"><b>38</b><br><sub>endpoints</sub></td>
<td align="center"><b>209</b><br><sub>tests</sub></td>
<td align="center"><b>11</b><br><sub>tables</sub></td>
<td align="center"><b>Java 17</b><br><sub>Spring Boot 4</sub></td>
</tr>
</table>

<br>

### What it does

- **Owner console** — create shipments, manage the rider roster, reassign
  failed deliveries, CSV-export reports
- **Rider app** — a mobile-first flow through pickup → transit →
  delivered / failed / returned
- **Public tracking** — no login, just the link the customer got by SMS
- **Auto-assignment** to the least-loaded active rider, serialized under a
  pessimistic lock so two shipments can't land on the same rider by race
- **A strict delivery state machine** with an immutable audit trail — every
  transition, forced or reassigned, is a permanent event
- **JWT auth** with SHA-256-hashed, rotating refresh tokens

<br>

### Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1 (WebMVC, Security, Data JPA, Validation) |
| Database | MySQL 8 + Flyway migrations |
| Auth | JWT (JJWT) — access + rotating refresh tokens |
| Frontend | React 18 · TypeScript · Vite |
| Build | Maven — `frontend-maven-plugin` bundles the React app into the JAR |

**Shipped as one JAR** — `frontend-maven-plugin` builds the React app into
`src/main/resources/static`, so there's no separate frontend deploy.

<br>

### Quick start

You'll need Java 17+, Maven 3.8+, and a local MySQL 8.

```bash
git clone https://github.com/thesunnycode/hyperlocal-delivery.git
cd hyperlocal-delivery

mysql -u root -p -e "CREATE DATABASE hyperlocal_delivery;"

cp .env.example .env
# edit .env: set DB_PASSWORD and JWT_SECRET (openssl rand -base64 32)

export $(cat .env | xargs)   # macOS/Linux — see .env.example for Windows

./mvnw spring-boot:run
```

Open `http://localhost:8080` for the app, or `http://localhost:8080/swagger-ui.html`
for the interactive API docs.

<br>

### Three engineering problems worth reading about

<details>
<summary><b>Concurrent auto-assignment with pessimistic locking</b></summary>
<br>

When a shipment is created, it's auto-assigned to the rider with the fewest
active shipments. Under concurrent requests, two shipments could land on
the same "least loaded" rider before either commits. Solved with a
`SELECT ... FOR UPDATE` pessimistic row lock on the rider query, serializing
assignment within a transaction — verified against real concurrent
requests, not just documented.
</details>

<details>
<summary><b>A strict state machine with an immutable audit trail</b></summary>
<br>

Shipments start life already `ASSIGNED` — there's no `CREATED` status. From
there:

```
ASSIGNED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED | FAILED | RETURNED
```

`DELIVERED` and `RETURNED` are terminal for everyone. `FAILED` is terminal
for the rider, but the owner can reassign a `FAILED` shipment back to
`ASSIGNED` — the only transition an owner can trigger. Invalid transitions
get a clean 422, never a silent no-op. Every transition — including a
non-terminal reassignment, where the status doesn't change but the rider
does — appends an immutable event record. The history can never be
rewritten.
</details>

<details>
<summary><b>Secure refresh-token rotation</b></summary>
<br>

Refresh tokens are SHA-256 hashed before storage — never stored in
plaintext. Each use revokes the old token and issues a new one; a scheduled
job purges expired ones. This closes token replay even if the database
itself is compromised.
</details>

<br>

### API overview

<details>
<summary>38 endpoints across auth, agents, shipments, delivery attempts, public tracking, and reports — click to expand</summary>

| Area | Method | Endpoint | Auth |
|---|---|---|---|
| Auth | POST | `/api/auth/register` · `/login` · `/refresh` · `/logout` | Public / JWT |
| Auth | GET, PATCH | `/api/auth/me` | JWT |
| Auth | POST | `/api/auth/forgot-password` · `/reset-password` | Public |
| Agents | POST, GET, PUT | `/api/agents`, `/api/agents/{id}` | Owner |
| Agents | POST | `/api/agents/{id}/deactivate` · `/reactivate` | Owner |
| Shipments | POST, GET | `/api/shipments`, `/api/shipments/{id}`, `/api/shipments/mine` | Owner / Agent |
| Shipments | POST | `/pickup` · `/start-transit` · `/out-for-delivery` · `/deliver` · `/return` · `/fail` · `/reassign` | Agent / Owner |
| Delivery attempts | POST, GET | `/api/shipments/{id}/attempt`, `/attempts` | Agent / Owner + Agent |
| Public tracking | GET | `/api/track/{token}` | Public |
| Analytics & reports | GET | `/api/analytics/*`, `/api/reports/*` (overview, trend, agent-performance, register, CSV export) | Owner |
| Health | GET | `/api/health` | Public |

Full request/response shapes are in the Swagger UI (`/swagger-ui.html`)
when the app is running.

**Key environment variables** (see `.env.example` for the full list):

| Variable | Required | Notes |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Yes | MySQL connection |
| `JWT_SECRET` | Yes | HS256 signing secret, 32+ bytes |
| `SMTP_HOST` | No | Blank = OTP/reset links log to console instead of emailing (fine for local dev) |
| `CORS_ORIGINS` | No | Defaults to `*` |

</details>
