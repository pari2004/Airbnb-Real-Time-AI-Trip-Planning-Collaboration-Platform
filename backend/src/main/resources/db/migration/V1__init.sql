-- ============================================================================
-- AI Trip War Room — initial schema (PRD Section 7.2.2, relational / PostgreSQL)
-- Optimistic concurrency via itinerary_items.position_version (PRD 7.2 guidance).
-- Money stored as NUMERIC (never float) so budget math is exact (PRD KPI: 100%).
-- ============================================================================

-- ---- Users ----------------------------------------------------------------
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          TEXT        NOT NULL UNIQUE,
    password_hash  TEXT        NOT NULL,
    display_name   TEXT        NOT NULL,
    role           TEXT        NOT NULL DEFAULT 'planner'
                   CHECK (role IN ('viewer','planner','leader','admin')),
    budget_ceiling NUMERIC(12,2),
    dietary_tags   TEXT[]      NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---- Refresh tokens (PRD 10.1 refresh-token flow) -------------------------
CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);

-- ---- Trips (Trip Rooms) ---------------------------------------------------
CREATE TABLE trips (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leader_user_id   BIGINT      NOT NULL REFERENCES users(id),
    destination      TEXT        NOT NULL,
    start_date       DATE        NOT NULL,
    end_date         DATE        NOT NULL,
    total_budget     NUMERIC(12,2) NOT NULL,
    invite_code      TEXT        NOT NULL UNIQUE,
    status           TEXT        NOT NULL DEFAULT 'drafting'
                     CHECK (status IN ('drafting','live','finalized')),
    max_participants INT         NOT NULL DEFAULT 50,
    ai_verbosity     TEXT        NOT NULL DEFAULT 'normal',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trips_leader ON trips(leader_user_id);

-- ---- Trip participants (per-room RBAC role — extends PRD 7.2) --------------
CREATE TABLE trip_participants (
    trip_id   BIGINT      NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_role TEXT        NOT NULL DEFAULT 'planner'
              CHECK (room_role IN ('viewer','planner','leader')),
    muted     BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, user_id)
);

-- ---- Venue metadata (authored dataset for RAG + constraints) --------------
CREATE TABLE venue_metadata (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    destination      TEXT        NOT NULL,
    name             TEXT        NOT NULL,
    category         TEXT        NOT NULL,          -- dinner, lodging, activity, ...
    price_per_person NUMERIC(10,2) NOT NULL CHECK (price_per_person >= 0),
    hours_open       TIME        NOT NULL,
    hours_close      TIME        NOT NULL,
    lat              DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng              DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    rating           NUMERIC(3,2) NOT NULL DEFAULT 4.00 CHECK (rating BETWEEN 0 AND 5),
    description      TEXT        NOT NULL DEFAULT '',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Proximity queries filter by destination then compute distance (PRD 7.2.2 note).
CREATE INDEX idx_venue_dest ON venue_metadata(destination);
CREATE INDEX idx_venue_dest_coords ON venue_metadata(destination, lat, lng);

-- ---- Venue dietary tags (many-to-many) ------------------------------------
CREATE TABLE venue_dietary_tags (
    venue_id BIGINT NOT NULL REFERENCES venue_metadata(id) ON DELETE CASCADE,
    tag      TEXT   NOT NULL,
    PRIMARY KEY (venue_id, tag)
);

-- ---- Itinerary items (board cards) ----------------------------------------
CREATE TABLE itinerary_items (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id           BIGINT      NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    venue_id          BIGINT      NOT NULL REFERENCES venue_metadata(id),
    day_index         INT         NOT NULL,
    start_time        TIME        NOT NULL,
    end_time          TIME        NOT NULL,
    lifecycle_stage   TEXT        NOT NULL DEFAULT 'drafting'
                      CHECK (lifecycle_stage IN ('drafting','voting','locked')),
    pinned_by_user_id BIGINT      NOT NULL REFERENCES users(id),
    position_version  INT         NOT NULL DEFAULT 0,   -- optimistic lock
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_items_trip ON itinerary_items(trip_id, day_index);

-- ---- Vote sessions (contested-slot voting) --------------------------------
CREATE TABLE vote_sessions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id            BIGINT      NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    item_id            BIGINT      NOT NULL REFERENCES itinerary_items(id) ON DELETE CASCADE,
    candidate_venue_ids BIGINT[]   NOT NULL DEFAULT '{}',
    opened_by_user_id  BIGINT      NOT NULL REFERENCES users(id),
    opened_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    closes_at          TIMESTAMPTZ NOT NULL,
    status             TEXT        NOT NULL DEFAULT 'open'
                       CHECK (status IN ('open','resolved','cancelled')),
    winning_venue_id   BIGINT
);
CREATE INDEX idx_vote_sessions_trip ON vote_sessions(trip_id, status);

-- ---- Item votes (one vote per user per session) ---------------------------
CREATE TABLE item_votes (
    vote_session_id BIGINT      NOT NULL REFERENCES vote_sessions(id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    choice_venue_id BIGINT      NOT NULL,
    cast_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (vote_session_id, user_id)
);

-- ---- Chat / event messages ------------------------------------------------
CREATE TABLE chat_messages (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id    BIGINT      NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id    BIGINT      REFERENCES users(id),        -- null for AI/system
    msg_type   TEXT        NOT NULL DEFAULT 'chat'
               CHECK (msg_type IN ('chat','ai_answer','system','conflict')),
    body       TEXT        NOT NULL,
    ts         BIGINT      NOT NULL,                      -- epoch millis
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_trip ON chat_messages(trip_id, id);

-- ---- Interactions (feeds recommendation engine) ---------------------------
CREATE TABLE interactions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id          BIGINT      REFERENCES trips(id) ON DELETE SET NULL,
    destination      TEXT        NOT NULL,
    category         TEXT        NOT NULL,
    venue_id         BIGINT      REFERENCES venue_metadata(id),
    liked            BOOLEAN     NOT NULL DEFAULT TRUE,
    co_traveled_with BIGINT[]    NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_interactions_user ON interactions(user_id);
CREATE INDEX idx_interactions_dest ON interactions(destination, category);

-- ---- Recommendations (materialized output of offline trainer) -------------
CREATE TABLE recommendations (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    venue_id     BIGINT      NOT NULL REFERENCES venue_metadata(id) ON DELETE CASCADE,
    destination  TEXT        NOT NULL,
    score        NUMERIC(8,5) NOT NULL,
    rank         INT         NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reco_user ON recommendations(user_id, rank);

-- ---- Session event log (observability + analytics reconciliation) ---------
CREATE TABLE session_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id        BIGINT      NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id        BIGINT      REFERENCES users(id),
    correlation_id TEXT,
    event_type     TEXT        NOT NULL,       -- joined,left,pin,move,lock,vote_open,vote_resolved,ai_ask,conflict,...
    payload        TEXT        NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_trip ON session_events(trip_id, created_at);
