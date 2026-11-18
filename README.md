# AI Trip War Room — Collaborative Group Itinerary Canvas & Real-Time Logistics Engine

A production-shaped prototype of a **real-time, multiplayer trip-planning platform**. Co-travellers
share a live itinerary canvas, pin and move activity/lodging cards that sync across clients in real
time, run budget-split votes, chat, and ask an **AI Concierge** that answers constraint-bound
questions grounded in a real venue/transit dataset.

- **Track:** A — Full-Stack (React client + REST API + WebSocket gateway)
- **Stack:** Java 17 · Spring Boot 3 · **Spring WebFlux** (reactive REST + WebSocket) · **Spring Data R2DBC** (non-blocking PostgreSQL) · Spring Security (JWT) · React 18 + Vite
- **Status:** Verified end-to-end live — 24 unit tests, 14 API smoke checks, 6 two-client WebSocket checks (see [Evidence](#evidence)).

> This is a single-operator build for local/private testing per the PRD. No public deployment, no
> real payment flow, no real third-party APIs — venue/transit data is authored locally.

---

## 1. Architecture at a glance

```
┌──────────── React Client (Vite) ─────────────┐     ┌──── Board Sync Gateway ────┐
│ day-by-day canvas · budget panel · chat       │◄──► │ RoomHub: Sinks.Many/room   │
│ AI Concierge panel · dashboard · rec rail     │ WS  │ presence · fan-out · votes │
└───────────────┬───────────────────────────────┘     └──────────────┬─────────────┘
                │ REST (JWT)                                           │
         ┌──────▼───────────── Application API (WebFlux) ─────────────▼──────┐
         │ auth/RBAC · trips · venues · board · budget · analytics · recs    │
         └──────┬───────────────────────────────────────────────────────────┘
         ┌──────▼──── AI Concierge Engine (pure Java, no external services) ──┐
         │ ConstraintMatcher · Ranker(40/30/30) · ConflictDetector           │
         │ RagRetriever (in-JVM TF-IDF) · LlmClient (template | OpenAI/Anthropic) │
         └──────┬───────────────────────────────────────────────────────────┘
         ┌──────▼──── Persistence: PostgreSQL via R2DBC + Flyway ────────────┐
         └───────────────────────────────────────────────────────────────────┘
```

Full detail in [`docs/architecture.md`](docs/architecture.md). API/WS contract in
[`docs/api-reference.md`](docs/api-reference.md).

### Key design decisions (deviations from the reference spec, per PRD §13.2)

1. **R2DBC instead of JPA/Hibernate.** The PRD's Option A mentions both WebFlux and JPA/HikariCP, but
   blocking JPA on the reactive Netty event loop would stall board fan-out and blow the <100 ms sync
   target. The whole data path is non-blocking (Spring Data R2DBC + `r2dbc-postgresql`). Flyway still
   runs migrations over JDBC at startup (migrations are a one-shot blocking op, which is fine).
2. **In-memory authoritative board state per room** (`RoomHub` `Sinks.Many`) with DB persistence of
   itinerary items; optimistic concurrency via `position_version` (no row locks) — PRD §7.2/§9.4.
3. **Per-room RBAC** via a `trip_participants(trip_id, user_id, room_role)` table (extends §7.2) so a
   user can be Leader of one trip and Planner in another; global `admin` governs dataset management.
4. **AI works with no external key by default.** The constraint matcher, ranker, conflict detector and
   RAG retrieval are pure Java; answer prose comes from a deterministic `TemplateLlmClient`. Set an
   LLM provider/key to swap in a real model — a grounding guard rejects any ungrounded output.
5. **Frontend in plain JavaScript (JSX) + Vite** (the "no TypeScript" rule applies only to the MERN row).

---

## 2. Prerequisites

- **JDK 17+**, **Maven 3.9+**
- **Node 18+** (for the React client)
- **Docker** (recommended — one-command bring-up) *or* a local PostgreSQL 16

---

## 3. Quick start (Docker Compose — everything at once)

```bash
cp .env.example .env            # then edit secrets (JWT_SECRET must be >= 32 bytes)
docker compose up --build
```

- API: http://localhost:8080  · Client: http://localhost:5173 · Postgres: localhost:5432
- The backend runs Flyway migrations and seeds the demo data (an admin + two travellers + the Lisbon
  venue dataset) on first boot.

## 4. Quick start (run each part manually)

**1) Database** (Docker):
```bash
docker run -d --name warroom-pg -e POSTGRES_DB=warroom -e POSTGRES_USER=warroom \
  -e POSTGRES_PASSWORD=warroom -p 5432:5432 postgres:16-alpine
```

**2) Backend:**
```bash
cd backend
export JWT_SECRET="change-me-to-a-long-random-secret-at-least-32-bytes"
# defaults already point at localhost:5432 / warroom / warroom
mvn spring-boot:run
```

**3) Frontend:**
```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

### Seeded demo logins

| Role (global) | Email | Password |
|---|---|---|
| admin | `admin@warroom.test` | `Admin123!` |
| planner | `leader@warroom.test` | `Leader123!` |
| planner | `traveler@warroom.test` | `Traveler123!` |

> `leader@warroom.test` becomes a **room Leader** automatically for any trip they create.

---

## 5. Try the full demo path (UI)

1. Log in as `leader@warroom.test`, **Create a Trip Room** (Lisbon is pre-filled). Copy the **invite code**.
2. In a second browser/incognito window, log in as `traveler@warroom.test` and **Join with the code**.
3. In the leader window, **pin** a venue onto a day → watch it appear live in the traveller window.
   **Drag** a card to another day → it moves for both. Versions bump; a stale drag is rejected with a
   conflict banner rather than silently overwriting.
4. **Ask the AI Concierge**: anchor on the pinned tour and ask *"Suggest a dinner spot near our 5 PM
   tour under $25/person"* → a grounded, constraint-valid answer with cited venues.
5. **Open a vote** on a contested card, cast votes from both windows, resolve → the winner applies to
   the board for everyone. Watch the **budget panel** and **chat** update live.
6. Log in as `admin@warroom.test` → **Admin** tab → upload/validate a new destination dataset.

---

## 6. Running the tests

**Unit tests** (constraint 100%-compliance, budget exactness, concurrency conflict, RBAC, Precision@5):
```bash
cd backend && mvn test
```

**End-to-end API smoke test** (needs the backend running + seeded):
```bash
bash scripts/smoke-test.sh                 # or: BASE=http://localhost:8081 bash scripts/smoke-test.sh
```

**Two-client real-time WebSocket test** (proves live multiplayer sync + drift):
```bash
node scripts/ws-multiplayer-test.mjs       # or: PORT=8081 node scripts/ws-multiplayer-test.mjs
```

---

## 7. Configuration (environment variables)

See [`.env.example`](.env.example). Highlights:

| Var | Default | Notes |
|---|---|---|
| `JWT_SECRET` | dev placeholder | **Must be ≥ 32 bytes.** Never commit the real value. |
| `R2DBC_URL` / `JDBC_URL` | localhost:5432/warroom | Point both at the same DB. |
| `LLM_PROVIDER` | `template` | `template` (no key) · `openai` · `anthropic` |
| `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` | — | Set to enable a real LLM for answer prose. |
| `CONCIERGE_RADIUS_KM` / `CONCIERGE_BUFFER_MIN` | 2.0 / 30 | Proximity radius & hours buffer (PRD §6.2). |
| `SEED_ENABLED` | `true` | Seeds demo users + Lisbon dataset on boot. |

**Secrets** (DB creds, JWT signing key, LLM key) are provided via env only — never committed (PRD §10.1).

---

## 8. Evidence

The exact runs below are reproducible with the scripts in `scripts/`. See
[`docs/EVIDENCE.md`](docs/EVIDENCE.md) for captured output and measured numbers.

- **Unit tests:** 24 passing — constraint matching (100% over 25 randomized requests), budget math
  (even/weighted/running, exact to the cent), board optimistic-concurrency conflict, RBAC, Precision@5 ≥ 0.6.
- **API smoke:** 14/14 — auth, trip, seeded catalog, stale-version move → 409 + reconcile state, valid
  move → 200, grounded AI suggestion, budget, vote open/cast/resolve, RBAC 403, invalid-upload 400, analytics.
- **Real-time:** 6/6 two-client — snapshot on join, presence, **leader pin propagated in ~36–44 ms**
  (target < 100 ms), chat propagation, ping/pong latency.

---

## 9. Repository layout

```
backend/      Spring Boot WebFlux + R2DBC API, WS gateway, AI engine, tests
frontend/     React + Vite client (Track A)
scripts/      smoke-test.sh, ws-multiplayer-test.mjs (reproducible evidence)
docs/         architecture.md, api-reference.md, postman_collection.json, EVIDENCE.md, RETROSPECTIVE.md
docker-compose.yml, .env.example
```

## 10. Scope & limitations

Honest list in [`docs/RETROSPECTIVE.md`](docs/RETROSPECTIVE.md). In brief: single-operator/local only;
dietary constraints resolved from the requester + parsed question (no per-slot attendance model);
RAG "retrieval" is in-JVM TF-IDF over the authored catalogue (no vector DB); recommendations retrain
as a batch job. All within the PRD's "smallest version that satisfies the acceptance criteria" guidance.
