CREATE SEQUENCE match_arena_number_seq
    AS BIGINT
    START WITH 100000
    MINVALUE 100000
    MAXVALUE 999999
    NO CYCLE
    CACHE 1;

CREATE TABLE match_arenas (
    id UUID PRIMARY KEY,
    arena_number INTEGER NOT NULL UNIQUE
        DEFAULT nextval('match_arena_number_seq'),
    owner_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    lobby_id BIGINT NOT NULL REFERENCES region_lobbies(lobby_id) ON DELETE RESTRICT,
    remark VARCHAR(4) NOT NULL DEFAULT '',
    level VARCHAR(16) NOT NULL,
    mode VARCHAR(24) NOT NULL,
    cost_type VARCHAR(16) NOT NULL,
    original_pay_type SMALLINT NOT NULL,
    daily_room_card_limit BIGINT NOT NULL,
    room_card_centi BIGINT NOT NULL DEFAULT 0,
    visible_to_strangers BOOLEAN NOT NULL DEFAULT TRUE,
    auto_transfer_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_transfer_threshold BIGINT NOT NULL DEFAULT 50,
    auto_transfer_amount BIGINT NOT NULL DEFAULT 0,
    low_card_reminder_threshold BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (owner_user_id, idempotency_key),
    CHECK (arena_number BETWEEN 100000 AND 999999),
    CHECK (level IN ('LEGACY', 'JUNIOR', 'INTERMEDIATE', 'SENIOR')),
    CHECK (mode IN ('LEADER', 'PREPAID', 'CIRCULATION', 'LOBBY_CARD')),
    CHECK (cost_type IN ('CHAMPION', 'AA')),
    CHECK (original_pay_type IN (0, 7, 22, 23, 24, 999)),
    CHECK (daily_room_card_limit > 0),
    CHECK (room_card_centi >= 0),
    CHECK (visible_to_strangers),
    CHECK (auto_transfer_threshold = 50),
    CHECK (auto_transfer_amount >= 0),
    CHECK (NOT auto_transfer_enabled OR auto_transfer_amount > 0),
    CHECK (auto_transfer_enabled OR auto_transfer_amount = 0),
    CHECK (mode <> 'LOBBY_CARD' OR NOT auto_transfer_enabled),
    CHECK (mode = 'LEADER' OR daily_room_card_limit = 888888),
    CHECK (
        (mode = 'LEADER' AND cost_type = 'CHAMPION' AND original_pay_type = 0)
        OR (mode = 'LEADER' AND cost_type = 'AA' AND original_pay_type = 24)
        OR (mode = 'PREPAID' AND cost_type = 'CHAMPION' AND original_pay_type = 0)
        OR (mode = 'PREPAID' AND cost_type = 'AA' AND original_pay_type = 999)
        OR (mode = 'CIRCULATION' AND cost_type = 'AA' AND original_pay_type = 7)
        OR (mode = 'LOBBY_CARD' AND cost_type = 'CHAMPION' AND original_pay_type = 23)
        OR (mode = 'LOBBY_CARD' AND cost_type = 'AA' AND original_pay_type = 22)
    ),
    CHECK (low_card_reminder_threshold IS NULL OR low_card_reminder_threshold > 0),
    CHECK (status IN ('OPEN', 'PAUSED', 'DISSOLVED'))
);

CREATE INDEX idx_match_arenas_owner_status_created
    ON match_arenas(owner_user_id, status, created_at DESC);
CREATE INDEX idx_match_arenas_lobby_status_created
    ON match_arenas(lobby_id, status, created_at DESC);

CREATE TABLE match_arena_members (
    id UUID PRIMARY KEY,
    arena_id UUID NOT NULL REFERENCES match_arenas(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (arena_id, user_id),
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CHECK (status IN ('ACTIVE', 'LEFT', 'BANNED'))
);

CREATE INDEX idx_match_arena_members_user_status_joined
    ON match_arena_members(user_id, status, joined_at DESC);

CREATE TABLE match_arena_card_ledger (
    id UUID PRIMARY KEY,
    arena_id UUID NOT NULL REFERENCES match_arenas(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    amount_centi BIGINT NOT NULL,
    reason VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (amount_centi > 0),
    CHECK (reason IN ('INITIAL_FUNDING', 'MANUAL_TRANSFER', 'AUTO_TRANSFER'))
);

CREATE UNIQUE INDEX uq_match_arena_initial_funding
    ON match_arena_card_ledger(arena_id, reason)
    WHERE reason = 'INITIAL_FUNDING';
