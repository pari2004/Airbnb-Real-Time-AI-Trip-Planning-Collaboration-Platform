# Architecture Overview — AI Trip War Room

Extends PRD §7 with what was actually built. Stack: Spring Boot 3 · Spring WebFlux · Spring Data
R2DBC (PostgreSQL) · Spring Security (JWT) · React 18 + Vite.

## 1. Layered view

| Layer | Responsibility | Key types |
|---|---|---|
| **Client (Track A)** | Day-by-day canvas, budget panel, chat, AI panel, dashboard, rec rail | `frontend/src/components/*`, `ws.js`, `api.js` |
| **Application API (WebFlux REST)** | Auth/RBAC, trips, venues, board, budget, votes, dashboard, analytics, admin | `*Controller` |
| **Board Sync Gateway (WebFlux WS)** | Per-room fan-out, presence, snapshots, latency | `TripWebSocketHandler`, `RoomHub`, `BoardCommandService` |
| **AI Concierge Engine (pure Java)** | Hard-constraint filtering, ranking, conflict detection, RAG, answer generation | `ConstraintMatcher`, `Ranker`, `ConflictDetector`, `RagRetriever`, `LlmClient` |
| **Persistence (R2DBC + Flyway)** | Non-blocking PostgreSQL access; schema migrations | `*Repository`, `db/migration/V1__init.sql` |

### Why one process, cleanly separated

The Board Sync Gateway and the AI Concierge Engine are **logically separate modules** that happen to
run in one process for this project's scope — exactly as PRD §9.4 recommends, so a future team could
scale them independently. `BoardCommandService` is the single orchestration point invoked by **both**
the WebSocket handler and the REST controllers, so board/chat/vote/conflict logic is written once.

## 2. Data model (ER)

```mermaid
erDiagram
    users ||--o{ trips : "leads"
    users ||--o{ trip_participants : "member of"
    trips ||--o{ trip_participants : has
    trips ||--o{ itinerary_items : contains
    venue_metadata ||--o{ itinerary_items : "pinned as"
    venue_metadata ||--o{ venue_dietary_tags : tagged
    trips ||--o{ vote_sessions : has
    itinerary_items ||--o{ vote_sessions : "contested by"
    vote_sessions ||--o{ item_votes : collects
    users ||--o{ item_votes : casts
    trips ||--o{ chat_messages : logs
    users ||--o{ interactions : generates
    users ||--o{ recommendations : receives
    trips ||--o{ session_events : "event log"
    users ||--o{ refresh_tokens : holds

    users { bigint id PK; text email UK; text password_hash; text role; numeric budget_ceiling; text_array dietary_tags }
    trips { bigint id PK; bigint leader_user_id FK; text destination; date start_date; date end_date; numeric total_budget; text invite_code UK; text status }
    trip_participants { bigint trip_id FK; bigint user_id FK; text room_role; bool muted }
    venue_metadata { bigint id PK; text destination; text name; text category; numeric price_per_person; time hours_open; time hours_close; float lat; float lng; numeric rating }
    venue_dietary_tags { bigint venue_id FK; text tag }
    itinerary_items { bigint id PK; bigint trip_id FK; bigint venue_id FK; int day_index; time start_time; time end_time; text lifecycle_stage; int position_version }
    vote_sessions { bigint id PK; bigint trip_id FK; bigint item_id FK; bigint_array candidate_venue_ids; timestamptz closes_at; text status; bigint winning_venue_id }
    item_votes { bigint vote_session_id FK; bigint user_id FK; bigint choice_venue_id }
    chat_messages { bigint id PK; bigint trip_id FK; bigint user_id FK; text msg_type; text body; bigint ts }
    interactions { bigint id PK; bigint user_id FK; text destination; text category; bigint venue_id; bool liked }
    recommendations { bigint id PK; bigint user_id FK; bigint venue_id FK; numeric score; int rank }
    session_events { bigint id PK; bigint trip_id FK; bigint user_id FK; text event_type; text payload }
```

Public API identifiers are the numeric PKs with a type prefix (`u_`, `tr_`, `v_`, `it_`) via
`PublicId`; decoding is lenient so both `tr_44210` and `44210` resolve.

## 3. Concurrent board edits without locks (PRD §7.2 / §9.4)

`itinerary_items.position_version` is an optimistic-concurrency token.

```
UPDATE itinerary_items
   SET day_index = ?, start_time = ?, end_time = ?, position_version = position_version + 1
 WHERE id = ? AND position_version = ? AND lifecycle_stage <> 'locked';
```

- **1 row updated** → applied; the new state is broadcast as `trip.board.update`.
- **0 rows updated** → the caller's version is stale (or the item is locked). The server returns the
  **current authoritative state** with a reason (`version_mismatch` / `item_locked`); over WebSocket the
  losing client additionally receives a targeted `trip.board.conflict` so its UI reconciles — never a
  silent overwrite. Over REST the same outcome is a `409` with the current item in the body.

No database row locks are taken. Verified by `BoardConcurrencyTest` and the live smoke test.

## 4. Real-time fan-out

`RoomHub` keeps one `Sinks.Many<Envelope>` multicast stream and a presence map per `tripId`. Each
socket's outbound is `concat(snapshot + room-state, merge(room-stream, personal-stream))`:

- **snapshot** (sent once on join): all items, budget summary, chat history, presence count, status.
- **room-stream**: broadcasts shared by everyone (board/chat/vote/presence/budget/conflict/AI).
- **personal-stream**: targeted replies to just this socket (move conflicts, pong, errors).

The handshake authenticates via `?token=<jwt>` (the same JWT as REST) and verifies room membership
before admitting the socket. Leader disconnect flips the room into a `leader_disconnected` state.

## 5. AI Concierge pipeline (PRD §6.2)

```
question + anchor + requester
      │
      ▼  QuestionParser (budget / dietary / category from NL)  +  ConstraintContext
      ▼  ConstraintMatcher.filter → only venues satisfying ALL active hard constraints
      │     budget ≤ ceiling · dietary all-match · hours contain slot (+buffer) · proximity ≤ radius
      ▼  RagRetriever (in-JVM TF-IDF over descriptions) → relevance scorer
      ▼  Ranker (40% proximity · 30% rating · 30% budget headroom) → ordered candidates
      ▼  LlmClient.generate (template by default; OpenAI/Anthropic adapter if keyed)
      ▼  grounding guard → answer cites only real candidate venues; else hedges (no suggestion)
```

The four hard constraints are deterministic and unit-tested for 100% compliance; the LLM only writes
prose over an already-valid set, so the answer can never recommend an out-of-constraint or invented venue.

## 6. Recommendation engine (PRD Epic 3)

Hybrid of **content-based** (destination/category/dietary affinity, cosine) and **user-based
collaborative filtering** (cosine neighbours over interaction vectors), blended `0.6·content +
0.4·CF`, min-max normalised. Trained **offline** (on new interactions, on a 5-min schedule, or via an
admin endpoint) and **materialised** to the `recommendations` table so the rail updates without a
redeploy. Evaluated by `RecommendationEvaluator` (Precision@5 ≥ 0.6).

## 7. Security (PRD §10.1)

- Stateless JWT access tokens (≤ 1 h) + rotating single-use refresh tokens; BCrypt password hashes.
- Four-role RBAC: global `admin` (dataset management) gated by `/api/v1/admin/**` + method security;
  room roles `viewer/planner/leader` enforced server-side by `TripAccessService` on every room action.
- WebSocket authenticated at handshake and re-checked on sensitive actions (only a Leader may lock/override).
- Chat and uploads sanitised/validated server-side; secrets via env only.

## 8. Observability (PRD §10.3)

- Correlation id per request (`CorrelationIdFilter`, `X-Correlation-Id`), surfaced in logs.
- Append-only `session_events` log drives analytics, which reconcile exactly against it.
- `/health/api` (DB check) and `/health/sync` (gateway) health endpoints.
