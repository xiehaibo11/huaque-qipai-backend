-- 金币场（原版"休闲场"/大众玩法）目录。
--
-- 原版把同一款麻将拆成两个 ConfID：房卡玩法 30109（TaiZhou.TaiZhouMahjong，
-- IsGoldMode=BOTNo）与金币场 30400（GoldTaiZhouMahjong，IsGoldMode=BOTYes，
-- DefaultBoxGameId=30109）。大厅主格子进的是金币场，见
-- android/docs/ORIGINAL-GOLD-CHOOSE-ROOM-EVIDENCE.md。
--
-- 档位数值在原版是服务端下发（GoldConfigManager.lua:504-514 把 base_score /
-- big_min_score / big_max_score / dynamic_cost 覆盖进本地配置中心），客户端归档里
-- 没有对应配置文件。本表的台州麻将三档取自 1.5.4 实机截图，属南北娱乐兼容配置，
-- 不是恢复出的原版服务端目录算法。

CREATE TABLE gold_games (
    lobby_id BIGINT NOT NULL REFERENCES region_lobbies(lobby_id),
    game_id BIGINT NOT NULL,
    display_name VARCHAR(40) NOT NULL,
    -- 原版 GameSub.lua 的 DefaultBoxGameId：金币场复用哪个房卡玩法的牌桌与规则基类。
    box_game_id BIGINT,
    -- 原版 roomInfo.GoldMode；50 表示选场页请求真实在线人数。
    gold_mode INTEGER NOT NULL DEFAULT 50,
    chair_count INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (lobby_id, game_id),
    CONSTRAINT ck_gold_games_chair_count CHECK (chair_count BETWEEN 2 AND 4)
);

CREATE INDEX idx_gold_games_lobby_sort
    ON gold_games(lobby_id, enabled, sort_order);

CREATE TABLE gold_game_levels (
    lobby_id BIGINT NOT NULL,
    game_id BIGINT NOT NULL,
    -- 原版 roomnameflag，同时是 roomFlag 数组成员；卡面配色 UIType = room_name_flag % 10。
    room_name_flag INTEGER NOT NULL,
    chair_count INTEGER NOT NULL,
    -- 原版 base_score；dynamic_cost 为真时底分显示为 "<base_score>以上"。
    base_score BIGINT NOT NULL,
    dynamic_cost BOOLEAN NOT NULL DEFAULT FALSE,
    -- 原版 big_min_score / big_max_score；max_rich = -1 表示无上限（显示"以上"）。
    min_rich BIGINT NOT NULL,
    max_rich BIGINT NOT NULL,
    -- 原版 roomInfo.Tag 的 LT / RT / CR；CR 最多两条，格式 "<crType>#<RRGGBB_文本|文本>"。
    tag_lt VARCHAR(32),
    tag_rt VARCHAR(32),
    tag_cr_1 VARCHAR(160),
    tag_cr_2 VARCHAR(160),
    sort_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (lobby_id, game_id, room_name_flag),
    FOREIGN KEY (lobby_id, game_id)
        REFERENCES gold_games(lobby_id, game_id) ON DELETE CASCADE,
    CONSTRAINT ck_gold_game_levels_flag CHECK (room_name_flag > 0),
    CONSTRAINT ck_gold_game_levels_base_score CHECK (base_score > 0),
    CONSTRAINT ck_gold_game_levels_min_rich CHECK (min_rich >= 0),
    CONSTRAINT ck_gold_game_levels_max_rich
        CHECK (max_rich = -1 OR max_rich > min_rich)
);

CREATE INDEX idx_gold_game_levels_sort
    ON gold_game_levels(lobby_id, game_id, enabled, sort_order);

-- 台州区（900023）台州麻将金币场 30400，复用 30109 牌桌，四人场。
INSERT INTO gold_games
    (lobby_id, game_id, display_name, box_game_id, gold_mode, chair_count, sort_order, enabled)
VALUES
    (900023, 30400, '台州麻将', 30109, 50, 4, 10, TRUE)
ON CONFLICT (lobby_id, game_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    box_game_id = EXCLUDED.box_game_id,
    gold_mode = EXCLUDED.gold_mode,
    chair_count = EXCLUDED.chair_count,
    sort_order = EXCLUDED.sort_order,
    enabled = EXCLUDED.enabled;

-- 三档取自 1.5.4 实机截图：新手场 底200以上 / 1000-6万，进阶场 底600以上 / 3万-20万，
-- 高级场 底1000以上 / 5万以上。绶带 crType 与文字颜色取自无加载遮罩的实机截图直接测量：
-- 进阶场蓝带(Img_cd_2)白字 FFFFFF，高级场紫带(Img_cd_3)黄字 EEEE55 (238,238,85)。
INSERT INTO gold_game_levels
    (lobby_id, game_id, room_name_flag, chair_count, base_score, dynamic_cost,
     min_rich, max_rich, tag_lt, tag_rt, tag_cr_1, tag_cr_2, sort_order, enabled)
VALUES
    (900023, 30400, 1, 4, 200, TRUE, 1000, 60000,
     NULL, NULL, NULL, NULL, 10, TRUE),
    (900023, 30400, 2, 4, 600, TRUE, 30000, 200000,
     NULL, NULL, '2#底分进阶，挑战高分', NULL, 20, TRUE),
    (900023, 30400, 3, 4, 1000, TRUE, 50000, -1,
     NULL, NULL, '3#EEEE55_支持加倍！强者之战', NULL, 30, TRUE)
ON CONFLICT (lobby_id, game_id, room_name_flag) DO UPDATE SET
    chair_count = EXCLUDED.chair_count,
    base_score = EXCLUDED.base_score,
    dynamic_cost = EXCLUDED.dynamic_cost,
    min_rich = EXCLUDED.min_rich,
    max_rich = EXCLUDED.max_rich,
    tag_lt = EXCLUDED.tag_lt,
    tag_rt = EXCLUDED.tag_rt,
    tag_cr_1 = EXCLUDED.tag_cr_1,
    tag_cr_2 = EXCLUDED.tag_cr_2,
    sort_order = EXCLUDED.sort_order,
    enabled = EXCLUDED.enabled;
