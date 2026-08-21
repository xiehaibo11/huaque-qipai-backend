CREATE TABLE room_tool_reservations (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    tool_type VARCHAR(32) NOT NULL,
    target_round INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (session_id, user_id, tool_type, target_round),
    CHECK (tool_type IN ('CHANGE_CARD', 'SHUFFLE')),
    CHECK (target_round > 0)
);

CREATE INDEX idx_room_tool_reservations_member
    ON room_tool_reservations(session_id, user_id, active);

CREATE TABLE room_tool_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE RESTRICT,
    sender_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    message_type VARCHAR(32) NOT NULL,
    content_index INTEGER,
    voice_media_type VARCHAR(32),
    voice_duration_ms INTEGER,
    voice_data BYTEA,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (message_type IN ('QUICK_PHRASE', 'EMOJI', 'VOICE')),
    CHECK (content_index IS NULL OR content_index >= 0),
    CHECK (voice_duration_ms IS NULL OR voice_duration_ms BETWEEN 400 AND 30000),
    CHECK (
        (message_type IN ('QUICK_PHRASE', 'EMOJI')
            AND content_index IS NOT NULL
            AND voice_media_type IS NULL
            AND voice_duration_ms IS NULL
            AND voice_data IS NULL)
        OR
        (message_type = 'VOICE'
            AND content_index IS NULL
            AND voice_media_type = 'audio/mp4'
            AND voice_duration_ms IS NOT NULL
            AND voice_data IS NOT NULL
            AND octet_length(voice_data) BETWEEN 1 AND 524288)
    )
);

CREATE INDEX idx_room_tool_messages_session_time
    ON room_tool_messages(session_id, created_at DESC, id DESC);

CREATE TABLE room_tool_operations (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, idempotency_key)
);

CREATE TABLE user_fortune_states (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE RESTRICT,
    wealth_points INTEGER NOT NULL DEFAULT 0,
    luck_points INTEGER NOT NULL DEFAULT 0,
    caishen_expires_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (wealth_points >= 0),
    CHECK (luck_points >= 0)
);

CREATE TABLE user_fortune_treasures (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    treasure_code VARCHAR(32) NOT NULL,
    level INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, treasure_code),
    CHECK (level BETWEEN 1 AND 10)
);

CREATE TABLE fortune_operations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, idempotency_key)
);
