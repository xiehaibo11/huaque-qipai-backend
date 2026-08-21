CREATE SEQUENCE public_player_id_seq
    START WITH 1000000000
    INCREMENT BY 1;

CREATE TABLE player_profiles (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    public_player_id BIGINT NOT NULL UNIQUE,
    avatar_key VARCHAR(120) NOT NULL,
    membership_level INTEGER NOT NULL DEFAULT 0
        CHECK (membership_level >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE player_wallets (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    room_cards BIGINT NOT NULL DEFAULT 0 CHECK (room_cards >= 0),
    coins BIGINT NOT NULL DEFAULT 0 CHECK (coins >= 0),
    diamonds BIGINT NOT NULL DEFAULT 0 CHECK (diamonds >= 0),
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE game_home_entries (
    code VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    route VARCHAR(120) NOT NULL,
    icon_key VARCHAR(120) NOT NULL,
    sort_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    lobby_id BIGINT REFERENCES region_lobbies(lobby_id)
);

CREATE INDEX idx_game_home_entries_enabled_sort
    ON game_home_entries(enabled, sort_order);

INSERT INTO game_home_entries (
    code,
    display_name,
    entry_type,
    route,
    icon_key,
    sort_order,
    enabled,
    lobby_id
) VALUES
    ('CREATE_ROOM', '创建房间', 'PRIMARY', 'room/create',
        'home_icon_create_room', 10, TRUE, NULL),
    ('JOIN_ROOM', '加入房间', 'PRIMARY', 'room/join',
        'home_icon_join_room', 20, TRUE, NULL),
    ('MATCH', '比赛场', 'PRIMARY', 'match',
        'home_game_mahjong', 30, TRUE, NULL),
    ('PEAK_MATCH', '巅峰赛', 'PRIMARY', 'match/peak',
        'home_game_qxbp', 40, TRUE, NULL),
    ('AN_DOU_SHUANG_KOU', '暗斗双扣', 'GAME', 'game/andou-shuangkou',
        'home_game_an_dou', 100, TRUE, NULL),
    ('QI_XING_BAO_PAI', '七星宝牌', 'GAME', 'game/qixing-baopai',
        'home_game_poker', 110, TRUE, NULL),
    ('QIAN_BIAN_SHUANG_KOU', '千变双扣', 'GAME',
        'game/qianbian-shuangkou', 'home_game_qxbp', 120, TRUE, NULL),
    ('AN_DOU_PIN_SHI', '暗斗拼十', 'GAME', 'game/andou-pinshi',
        'home_game_an_dou', 130, TRUE, NULL),
    ('SHI_SAN_ZHANG', '十三张', 'GAME', 'game/shisanzhang',
        'home_game_poker', 140, TRUE, NULL),
    ('GAME_CENTER', '游戏中心', 'GAME', 'game-center',
        'home_icon_game_center', 150, TRUE, NULL),
    ('TAIZHOU_MAHJONG', '台州麻将', 'GAME', 'game/taizhou-mahjong',
        'home_game_mahjong', 160, TRUE, 900023),
    ('MORE_GAMES', '更多游戏', 'GAME', 'games/more',
        'home_label_more_games', 170, TRUE, NULL),
    ('SHOP', '商城', 'NAVIGATION', 'shop',
        'home_icon_store', 200, TRUE, NULL),
    ('WARDROBE', '装扮', 'NAVIGATION', 'wardrobe',
        'home_label_membership', 210, TRUE, NULL),
    ('RECORD', '战绩', 'NAVIGATION', 'record',
        'home_game_poker', 220, TRUE, NULL),
    ('ACTIVITY', '活动', 'NAVIGATION', 'activity',
        'home_icon_coin_rewards', 230, TRUE, NULL),
    ('SHARE', '分享', 'NAVIGATION', 'share',
        'home_icon_create_room', 240, TRUE, NULL),
    ('BAG', '背包', 'NAVIGATION', 'bag',
        'home_icon_bag', 250, TRUE, NULL),
    ('MAIL', '邮件', 'NAVIGATION', 'mail',
        'home_icon_mail', 260, TRUE, NULL),
    ('MORE', '更多', 'NAVIGATION', 'more',
        'home_icon_settings', 270, TRUE, NULL),
    ('CUSTOMER_SERVICE', '客服', 'TOP', 'customer-service',
        'home_icon_customer_service', 300, TRUE, NULL),
    ('COIN_REWARDS', '获取金币', 'TOP', 'coin-rewards',
        'home_icon_coin_rewards', 310, TRUE, NULL);
