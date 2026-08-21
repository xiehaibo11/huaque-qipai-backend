CREATE TABLE game_sessions (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL UNIQUE REFERENCES game_rooms(id) ON DELETE RESTRICT,
    game_id BIGINT NOT NULL,
    phase VARCHAR(32) NOT NULL,
    round_number INTEGER NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    state JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (game_id > 0),
    CHECK (phase IN ('WAITING', 'DEALING', 'PLAYING', 'ROUND_RESULT', 'COMPLETED', 'DISSOLVED')),
    CHECK (round_number >= 0),
    CHECK (revision >= 0)
);

CREATE TABLE game_session_seats (
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE RESTRICT,
    seat_number SMALLINT NOT NULL,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    ready BOOLEAN NOT NULL DEFAULT FALSE,
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    last_ack_revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, seat_number),
    UNIQUE (session_id, user_id),
    CHECK (seat_number > 0),
    CHECK (last_ack_revision >= 0)
);

CREATE INDEX idx_game_session_seats_user
    ON game_session_seats(user_id, session_id);

CREATE TABLE game_commands (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    expected_revision BIGINT NOT NULL,
    accepted_revision BIGINT,
    result JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, idempotency_key),
    CHECK (expected_revision >= 0),
    CHECK (accepted_revision IS NULL OR accepted_revision > 0)
);

CREATE INDEX idx_game_commands_session_revision
    ON game_commands(session_id, accepted_revision);

CREATE TABLE game_events (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE RESTRICT,
    revision BIGINT NOT NULL,
    event_order SMALLINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    target_seat SMALLINT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, revision, event_order),
    FOREIGN KEY (session_id, target_seat)
        REFERENCES game_session_seats(session_id, seat_number) ON DELETE RESTRICT,
    CHECK (revision > 0),
    CHECK (event_order > 0),
    CHECK (visibility IN ('PUBLIC', 'SEAT')),
    CHECK ((visibility = 'PUBLIC' AND target_seat IS NULL)
        OR (visibility = 'SEAT' AND target_seat IS NOT NULL))
);

CREATE INDEX idx_game_events_session_order
    ON game_events(session_id, revision, event_order);

CREATE TABLE game_round_results (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE RESTRICT,
    round_number INTEGER NOT NULL,
    public_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    internal_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, round_number),
    CHECK (round_number > 0)
);
