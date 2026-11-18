# Retrospective — AI Trip War Room

## What was built

A complete, working, single-stack full-stack system (Track A) on Spring Boot WebFlux + R2DBC + React:

- **Epic 1 — Real-time War Room:** JWT auth + four-role RBAC; Trip Room creation/invite/join; a live
  itinerary canvas with a `drafting → voting → locked` lifecycle; pin/move/lock fan-out over a
  per-room reactive sink; **lock-free optimistic concurrency** (`position_version`) with explicit
  conflict reconciliation; presence, chat (sanitised), typing, reconnect/resync via join snapshots;
  leader-disconnect handling; latency ping/pong.
- **Epic 2 — AI Concierge & logistics:** deterministic hard-constraint filter (budget, dietary
  all-match, operating hours + buffer, haversine proximity); 40/30/30 ranking; an in-JVM TF-IDF RAG
  retriever; a swappable `LlmClient` (deterministic template by default, OpenAI/Anthropic adapter when
  keyed) with a grounding guard; a conflict detector (schedule overlap + budget breach).
- **Epic 3 — Dashboard & analytics:** a hybrid content + collaborative-filtering recommender trained
  offline and materialised; personal dashboard + "Recommended for your group" rail; session analytics
  reconciled against an append-only event log; admin venue-dataset upload with validation.
- **Cross-cutting:** 24 unit tests, an end-to-end API smoke harness, a two-client WebSocket harness,
  Docker Compose, Flyway schema, a seeded Lisbon dataset covering every constraint type, and full docs.

## Deliberate scope decisions (PRD §13.2)

- **R2DBC over JPA/Hibernate.** Chosen to keep the reactive path non-blocking end-to-end; mixing
  blocking JPA into the Netty event loop would undermine the sync-drift target. Trade-off: no lazy
  loading / fewer conveniences; composite-key tables use `DatabaseClient` directly.
- **Dietary constraints** are resolved from the requester's profile + the parsed question, not a
  per-slot attendance model (the PRD's "travellers attending that slot"). Attendance tracking was out
  of scope for four weeks; the hard-constraint rule itself (all tags must match) is fully implemented.
- **RAG retrieval is lexical** (in-JVM TF-IDF over the authored descriptions) rather than embeddings +
  a vector DB. For a single authored catalogue this is sufficient, fully offline, and testable; the
  structured 40/30/30 ranking remains the spec-mandated ordering.
- **Recommendations retrain as a batch job** (on new interactions, on a schedule, or admin-triggered)
  and are materialised — the rail updates without a redeploy, which is what the PRD accepts.
- **Category** is treated as a relevance filter distinct from the four hard constraints, so the
  "100% constraint compliance" guarantee is about budget/dietary/hours/proximity exactly as specified.

## Bugs found and fixed during live verification

- Seed dataset used `"24:00"` as a closing time, which is not a valid `LocalTime`; the strict
  all-or-nothing validator correctly rejected the whole dataset. Fixed the data to `"23:59"`
  (validation behaviour was right — the data was wrong).
- `uploadDataset` reported `inserted: 0` because it counted `Mono<Void>` emissions; switched to the
  input size after a successful insert. (Rows were always inserted correctly.)
- A client joining mid-session didn't learn the current presence count (the "joined" broadcast fires
  before its stream subscribes); added `presenceCount` to the join snapshot — the standard fix.

## What I'd do next with more time

1. **Per-slot attendance** so dietary constraints reflect exactly who is attending each activity.
2. **Embedding-based RAG** (local sentence embeddings) with the lexical TF-IDF as a fallback, for
   richer "near our 5 PM tour" semantic matching across larger catalogues.
3. **Conflict de-duplication / lifecycle** so the same standing conflict isn't re-broadcast every scan;
   mark conflicts resolved when the offending item moves.
4. **WebSocket integration tests in CI** (Testcontainers Postgres + a scripted client) to guard the
   real-time path automatically, complementing the manual harness.
5. **Horizontal scale** of the sync gateway via a Redis-backed `RoomHub` (pub/sub) so rooms can span
   instances — the module boundary is already in place for this.
6. **Richer moderation UI** (mute/remove/transfer-leader) surfaced in the client; the server endpoints
   and WS paths already exist.
