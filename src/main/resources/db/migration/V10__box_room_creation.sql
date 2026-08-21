-- 包厢（创建房间）系统：游戏目录、规则配置、房间与房卡结算。
-- 原版房卡消耗存在 0.25/0.5/1.5 等分数（平摊支付），因此余额与流水统一以
-- centi（百分之一张房卡）整数存储；对外展示的整数张数保留为生成列。

ALTER TABLE player_wallets
    ADD COLUMN room_card_centi BIGINT NOT NULL DEFAULT 0;

UPDATE player_wallets
SET room_card_centi = room_cards * 100;

ALTER TABLE player_wallets
    DROP COLUMN room_cards;

ALTER TABLE player_wallets
    ADD COLUMN room_cards BIGINT
        GENERATED ALWAYS AS (room_card_centi / 100) STORED;

ALTER TABLE player_wallets
    ADD CONSTRAINT ck_player_wallets_room_card_centi_nonnegative
        CHECK (room_card_centi >= 0);

CREATE TABLE room_games (
    lobby_id BIGINT NOT NULL REFERENCES region_lobbies(lobby_id),
    game_id BIGINT NOT NULL,
    display_name VARCHAR(40) NOT NULL,
    badge VARCHAR(24),
    sort_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (lobby_id, game_id)
);

CREATE INDEX idx_room_games_lobby_sort
    ON room_games(lobby_id, enabled, sort_order);

CREATE TABLE room_rule_configs (
    lobby_id BIGINT NOT NULL,
    game_id BIGINT NOT NULL,
    config_version INTEGER NOT NULL,
    config JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (lobby_id, game_id),
    FOREIGN KEY (lobby_id, game_id)
        REFERENCES room_games(lobby_id, game_id) ON DELETE CASCADE
);

CREATE TABLE game_rooms (
    id UUID PRIMARY KEY,
    room_number CHAR(6) NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    lobby_id BIGINT NOT NULL,
    game_id BIGINT NOT NULL,
    game_rule TEXT NOT NULL,
    room_rule TEXT NOT NULL,
    player_count INTEGER NOT NULL CHECK (player_count > 0),
    play_count INTEGER NOT NULL CHECK (play_count > 0),
    pay_type VARCHAR(8) NOT NULL CHECK (pay_type IN ('ALL', 'AA')),
    room_fee_centi INTEGER NOT NULL CHECK (room_fee_centi >= 0),
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('OPEN', 'CHARGED', 'DISSOLVED')),
    created_at TIMESTAMPTZ NOT NULL,
    first_round_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    FOREIGN KEY (lobby_id, game_id) REFERENCES room_games(lobby_id, game_id)
);

-- 同一房主同时只能持有一个未结束的包厢。
CREATE UNIQUE INDEX uk_game_rooms_owner_open
    ON game_rooms(owner_user_id)
    WHERE status <> 'DISSOLVED';

CREATE INDEX idx_game_rooms_status_created
    ON game_rooms(status, created_at);

CREATE TABLE room_card_ledger (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    room_id UUID REFERENCES game_rooms(id) ON DELETE SET NULL,
    amount_centi BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_room_card_ledger_user_created
    ON room_card_ledger(user_id, created_at DESC);

-- 每个房间只允许结算一次。
CREATE UNIQUE INDEX uk_room_card_ledger_room_charge
    ON room_card_ledger(room_id)
    WHERE reason = 'ROOM_CREATE';
