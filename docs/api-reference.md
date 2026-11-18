# API Reference — AI Trip War Room

Base URL (default): `http://localhost:8080`  ·  All bodies are JSON.
Authenticated routes require `Authorization: Bearer <accessToken>`.
Errors use a uniform body: `{ "status", "error", "message", "path", "timestamp" }`.

Public IDs are prefixed: users `u_`, trips `tr_`, venues `v_`, items `it_`.

---

## Authentication

### POST /api/v1/auth/register
```json
{ "email": "a@b.com", "password": "Passw0rd!", "displayName": "Ana",
  "budgetCeiling": 40, "dietaryTags": ["vegan"] }
```
`201` → `{ "accessToken", "refreshToken", "expiresIn", "user": { "id","email","displayName","role","budgetCeiling","dietaryTags" } }`

### POST /api/v1/auth/login
`{ "email", "password" }` → `200` same shape as register.

### POST /api/v1/auth/refresh
`{ "refreshToken" }` → `200` new token pair (refresh tokens are single-use / rotated).

### POST /api/v1/auth/logout   *(auth)* → `200`
### GET  /api/v1/auth/me       *(auth)* → the current `UserDto`

---

## Trips

### POST /api/v1/trips  *(auth)* — create a Trip Room
```json
{ "destination": "Lisbon, Portugal", "startDate": "2026-11-10",
  "endDate": "2026-11-14", "totalBudget": 1200, "maxParticipants": 8 }
```
`201` →
```json
{ "tripId": "tr_1", "inviteCode": "AB-7Q2K", "status": "drafting",
  "leaderUserId": "u_2", "createdAt": "2026-11-13T10:15:00Z" }
```

### GET  /api/v1/trips/{id} *(member)* → trip view with `participants[]`
### POST /api/v1/trips/join *(auth)* — `{ "inviteCode" }` → trip view (adds caller as Planner)
### POST /api/v1/trips/{id}/invite *(leader)* → `{ "inviteCode", "inviteLink" }`
### PATCH /api/v1/trips/{id}/settings *(leader)* — `{ "maxParticipants?","aiVerbosity?","totalBudget?","status?" }`
### POST /api/v1/trips/{id}/participants/role *(leader)* — `{ "userId":"u_9", "role":"viewer|planner" }`
### POST /api/v1/trips/{id}/transfer-leader *(leader)* — `{ "userId":"u_9" }`

---

## Venues

### GET /api/v1/destinations/{destination}/venues?near={lat,lng}&radiusKm={n} *(auth)*
`200` →
```json
{ "destination": "Lisbon, Portugal",
  "venues": [ { "venueId":"v_1","name":"Taberna da Rua","category":"dinner",
    "pricePerPerson":22,"dietaryTags":["vegetarian"],
    "hours":{"open":"11:00","close":"22:00"},
    "coordinates":{"lat":38.7139,"lng":-9.1394},"rating":4.6,"description":"..." } ] }
```

### POST /api/v1/admin/venues *(ADMIN)* — upload + validate a dataset
Body `{ "destination", "venues":[ { name, category, pricePerPerson, dietaryTags[], hours:{open,close}, coordinates:{lat,lng}, rating?, description? } ] }`.
Validates price ≥ 0, known dietary tags, lat/lng ranges, `close > open`. Rejects the whole upload on
the first failure with a clear message. `200` → `{ "destination", "inserted", "warnings":[] }`.

---

## Board  (REST mirror of the WebSocket commands)

### GET /api/v1/trips/{id}/board *(member)* → full snapshot `{ tripId, status, items[], budget, chatHistory[], presenceCount }`
### POST /api/v1/trips/{id}/board/items *(planner+)* — pin
`{ "venueId":"v_1", "dayIndex":0, "startTime":"17:00", "endTime":"19:00" }` → `201` `ItemView`
### POST /api/v1/trips/{id}/board/move *(planner+)* — optimistic move
`{ "itemId":"it_1", "dayIndex":1, "startTime":"17:00", "endTime":"19:00", "positionVersion":0 }`
→ `200 {applied:true,item}` or `409 {applied:false,item,conflictReason}` (current state to reconcile).
### POST /api/v1/trips/{id}/board/items/{itemId}/lock *(leader)* → `ItemView`
### POST /api/v1/trips/{id}/board/items/{itemId}/unlock *(leader)* → `ItemView`
### DELETE /api/v1/trips/{id}/board/items/{itemId} *(pinner or leader)* → `200`

`ItemView` = `{ itemId, venueId, dayIndex, startTime, endTime, lifecycleStage, positionVersion, pinnedByUserId }`.

---

## Budget

### GET /api/v1/trips/{id}/budget *(member)*
```json
{ "totalBudget":1200, "participantCount":2, "perPersonPlanned":45.50,
  "groupPlanned":91.00, "remaining":1109.00, "overBudget":false,
  "lineItems":[ { "itemId","venueName","pricePerPerson","runningPerPerson" } ],
  "evenSplit":[ { "userId","displayName","share" } ] }
```

---

## Voting

### POST /api/v1/trips/{id}/votes *(leader)* — open
`{ "itemId":"it_2", "candidateVenueIds":["v_1","v_5"], "durationSeconds":60 }` → `201` `VoteView`
### POST /api/v1/trips/{id}/votes/cast *(member)* — `{ "itemId":"it_2", "choiceVenueId":"v_5" }`
### POST /api/v1/trips/{id}/votes/resolve *(leader)* — `{ "itemId":"it_2" }` → `{ itemId, winningVenueId, tally }`

Votes also auto-resolve server-side when `closesAt` passes (timed vote). Winner is applied to the board.

---

## AI Concierge

### POST /api/v1/trips/{id}/ai/ask *(member)*
```json
{ "userId":"u_733", "question":"Suggest a dinner spot near our 5 PM tour under $25/person",
  "anchorItemId":"it_1" }
```
`200` →
```json
{ "answer":"Halal House Mouraria is about a 4-minute walk from your anchor, offers halal/vegetarian options, is open 11:30–22:30, and runs about $16 per person.",
  "suggestedVenueId":"v_3", "sourceEvents":["venue_3","venue_2"],
  "generatedAt":"2026-11-13T10:24:47Z",
  "candidates":[ { "venueId","name","score","distanceKm","pricePerPerson","dietaryTags" } ],
  "constraintsApplied":["budget <= $25/person","dietary: [vegetarian]","within 2.0km of anchor"],
  "grounded":true }
```
If no venue satisfies all constraints, `answer` hedges and `suggestedVenueId` is `null`.

---

## Dashboard, recommendations & analytics (Epic 3)

### GET  /api/v1/me/dashboard *(auth)* → `{ user, profile:{budgetCeiling,dietaryTags,favoredCategories[]}, activeRooms[], pastTrips[] }`
### GET  /api/v1/me/recommendations *(auth)* → `[ { venueId, name, destination, category, pricePerPerson, score, rank } ]`
### POST /api/v1/me/interactions *(auth)* — `{ destination, category, venueId?, liked?, tripId?, coTraveledWith?[] }` (records + async retrain)
### POST /api/v1/me/recommendations/retrain *(auth)* → `{ usersTrained, recommendationsWritten }`
### POST /api/v1/admin/recommendations/retrain *(ADMIN)* → same
### GET  /api/v1/trips/{id}/analytics *(member)*
```json
{ "tripId","planningDurationMinutes","peakConcurrentPlanners","aiQuestionsAsked",
  "conflictsFlagged","votesResolved","pinsAndMoves","totalEvents","finalBudget": { ...BudgetSummary } }
```

### Health
`GET /health/api` → `{status,db}` · `GET /health/sync` → `{status,gateway}`

---

## WebSocket contract  (PRD §7.3.4)

Connect: `ws://host/ws/trips/{tripId}?token=<accessToken>` (one socket per room).
Every frame shares the envelope: `{ "event", "tripId", "payload", "ts" }`.

### Inbound (client → server)

| Event | Payload |
|---|---|
| `trip.board.pin` | `{ venueId, dayIndex, startTime, endTime }` |
| `trip.board.move` | `{ itemId, dayIndex, startTime, endTime, positionVersion }` |
| `trip.board.lock` / `trip.board.unlock` | `{ itemId }` (leader) |
| `trip.board.remove` | `{ itemId }` |
| `trip.vote.openRequest` | `{ itemId, candidateVenueIds[], durationSeconds }` (leader) |
| `trip.vote.cast` | `{ itemId, choiceVenueId }` |
| `trip.vote.resolve` | `{ itemId }` (leader) |
| `trip.chat.send` | `{ body }` |
| `trip.typing` | `{ isTyping }` |
| `trip.sync.ping` | `{ clientSentAt }` |
| `trip.ai.ask` | `{ question, anchorItemId? }` |

### Outbound (server → clients)

| Event | Payload |
|---|---|
| `trip.sync.snapshot` | `{ tripId, status, items[], budget, chatHistory[], presenceCount }` (on join) |
| `trip.room.state` | `{ status, leaderConnected }` |
| `trip.board.update` | `ItemView` |
| `trip.board.remove` | `{ itemId }` |
| `trip.board.conflict` | `{ conflictType, itemIds[], details, current? }` |
| `trip.presence.update` | `{ participantId, action:'joined'|'left', participantCount }` |
| `trip.chat.message` | `{ messageId, userId, body, msgType, tsSeconds }` |
| `trip.typing.update` | `{ userId, isTyping }` |
| `trip.budget.update` | `BudgetSummary` |
| `trip.vote.open` | `{ voteSessionId, itemId, candidateVenueIds[], closesAt, status }` |
| `trip.vote.cast` | `{ itemId, userId, choiceVenueId }` |
| `trip.vote.resolved` | `{ itemId, winningVenueId, tally }` |
| `trip.ai.suggestion` | `{ userId, answer, suggestedVenueId, sourceEvents[], grounded }` |
| `trip.sync.pong` | `{ clientSentAt, serverReceivedAt }` |
| `trip.error` | `{ message }` |
