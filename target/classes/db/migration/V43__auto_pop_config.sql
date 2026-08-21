-- 自动弹窗调度配置（原版 lobby.Modules.AutoPop + lobby.Modules.PopSystem）。
--
-- 原版这两份配置由服务端 Configuration 模块随 AllConfig 下发，键名分别是
-- `autoPop` 与 `PopSystem`。本表的字段结构与种子数值直接取自设备缓存
-- artifacts/zhejiang_game_lobby_1.5.4/extracted/device-private/data/user/0/
-- com.xm.zjgamecenter/shared_prefs/Cocos2dxPrefsFile.xml 里
-- KW_CONFIGURATION_DATA_prod900038ConfigurationData_zhejiang-all-total 的
-- AllConfig.autoPop / AllConfig.PopSystem，属一次真实线上观测，不是推断值。
-- 算法与逐条证据见 android/docs/ORIGINAL-AUTO-POP-EVIDENCE.md。

CREATE TABLE auto_pop_candidates (
    id UUID PRIMARY KEY,
    -- 原版 AutoPop._checkList 里的模块名，也是 KW_AURO_POP_SHOW 与
    -- KW_POP_SYSTEM_CLOSE_INFO 的存储键。
    module_name VARCHAR(64) NOT NULL UNIQUE,
    -- 扫描优先级。原版取 AllConfig.autoPop 数组下标（1 基）写入 sort 后排序，
    -- 这里沿用同一顺序值。
    sort_order INTEGER NOT NULL,
    -- 南北娱乐是否真的注册该候选。缺活动服务端、给不出 isValid() 的模块登记
    -- 为 FALSE，只保留配置结构证据，不参与调度。
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (sort_order),
    CHECK (sort_order > 0)
);

CREATE TABLE auto_pop_scene_limits (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES auto_pop_candidates(id) ON DELETE CASCADE,
    -- 原版 popCfg 的键：场景来源 from，或聚合键 total。
    scene_name VARCHAR(64) NOT NULL,
    -- 原版 popCfg[scene] 是 {无奖上限, 有奖上限}，下标由模块 isHaveAward() 选。
    limit_without_award INTEGER NOT NULL,
    -- 原版长度 1 的配置没有第二档，此时 cfg[2] 为 nil；这里存 NULL，
    -- 服务端与客户端一致地把缺失档位当成上限 0（不弹），避免原版的空值崩溃面。
    limit_with_award INTEGER,
    UNIQUE (candidate_id, scene_name),
    CHECK (limit_without_award >= 0),
    CHECK (limit_with_award IS NULL OR limit_with_award >= 0)
);

CREATE TABLE pop_system_configs (
    id UUID PRIMARY KEY,
    -- 原版 AllConfig.PopSystem 先按 tostring(type) 取子表，取不到退回顶层默认；
    -- 顶层默认在这里用保留键 __default__ 表示。
    config_key VARCHAR(64) NOT NULL UNIQUE,
    -- 连续关闭多少次后进入免打扰。
    close_cnt INTEGER NOT NULL,
    -- 免打扰天数，endtime = now + cd_day * 86400；-1 表示永久。
    cd_day INTEGER NOT NULL,
    -- 当日关闭次数上限，原版只有部分模块配；NULL 表示不限当日次数。
    daily_cnt INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (close_cnt > 0),
    CHECK (cd_day = -1 OR cd_day > 0),
    CHECK (daily_cnt IS NULL OR daily_cnt > 0)
);

-- AllConfig.autoPop 的 8 项（原版 _checkList 有 9 个候选，InviteAct 未下发，
-- 原版在 onGetConfiguration 里把它 table.remove 掉，因此这里也不登记）。
-- 只有 TimeLoginAct 有南北娱乐自研活动服务端，能给出 isValid()/isHaveAward()，
-- 因此只有它 enabled = TRUE；其余七项是结构与优先级证据，不代表弹窗已可用。
INSERT INTO auto_pop_candidates (
    id, module_name, sort_order, enabled, created_at, updated_at
) VALUES
    ('9a1b0c30-0001-4a00-9000-000000000001', 'RecallNew', 1, FALSE, now(), now()),
    ('9a1b0c30-0002-4a00-9000-000000000002', 'goldFirstRecharge', 2, FALSE, now(), now()),
    ('9a1b0c30-0003-4a00-9000-000000000003', 'goldPeGP', 3, FALSE, now(), now()),
    ('9a1b0c30-0004-4a00-9000-000000000004', 'LuckyMission', 4, FALSE, now(), now()),
    ('9a1b0c30-0005-4a00-9000-000000000005', 'luckytask', 5, FALSE, now(), now()),
    ('9a1b0c30-0006-4a00-9000-000000000006', 'SxvipAct', 6, FALSE, now(), now()),
    ('9a1b0c30-0007-4a00-9000-000000000007', 'MonthlyCard', 7, FALSE, now(), now()),
    ('9a1b0c30-0008-4a00-9000-000000000008', 'TimeLoginAct', 8, TRUE, now(), now());

INSERT INTO auto_pop_scene_limits (
    id, candidate_id, scene_name, limit_without_award, limit_with_award
) VALUES
    -- RecallNew
    ('9a1b0c31-0001-4a00-9000-000000000001',
     '9a1b0c30-0001-4a00-9000-000000000001', 'bigwinlost', 1, NULL),
    ('9a1b0c31-0001-4a00-9000-000000000002',
     '9a1b0c30-0001-4a00-9000-000000000001', 'bigwinlostMatch', 1, NULL),
    ('9a1b0c31-0001-4a00-9000-000000000003',
     '9a1b0c30-0001-4a00-9000-000000000001', 'ingame', 1, NULL),
    ('9a1b0c31-0001-4a00-9000-000000000004',
     '9a1b0c30-0001-4a00-9000-000000000001', 'total', 3, NULL),
    -- goldFirstRecharge
    ('9a1b0c31-0002-4a00-9000-000000000001',
     '9a1b0c30-0002-4a00-9000-000000000002', 'toGoldHall', 1, NULL),
    ('9a1b0c31-0002-4a00-9000-000000000002',
     '9a1b0c30-0002-4a00-9000-000000000002', 'toGoldChooseRoom', 1, NULL),
    ('9a1b0c31-0002-4a00-9000-000000000003',
     '9a1b0c30-0002-4a00-9000-000000000002', 'closeBrokenGiftView', 1, NULL),
    ('9a1b0c31-0002-4a00-9000-000000000004',
     '9a1b0c30-0002-4a00-9000-000000000002', 'total', 3, NULL),
    -- goldPeGP
    ('9a1b0c31-0003-4a00-9000-000000000001',
     '9a1b0c30-0003-4a00-9000-000000000003', 'toGoldHall', 1, NULL),
    ('9a1b0c31-0003-4a00-9000-000000000002',
     '9a1b0c30-0003-4a00-9000-000000000003', 'toGoldChooseRoom', 1, NULL),
    ('9a1b0c31-0003-4a00-9000-000000000003',
     '9a1b0c30-0003-4a00-9000-000000000003', 'closeBrokenGiftView', 1, NULL),
    ('9a1b0c31-0003-4a00-9000-000000000004',
     '9a1b0c30-0003-4a00-9000-000000000003', 'total', 3, NULL),
    -- LuckyMission
    ('9a1b0c31-0004-4a00-9000-000000000001',
     '9a1b0c30-0004-4a00-9000-000000000004', 'bigwinlost', 1, 1),
    ('9a1b0c31-0004-4a00-9000-000000000002',
     '9a1b0c30-0004-4a00-9000-000000000004', 'goldleave', 1, 1),
    ('9a1b0c31-0004-4a00-9000-000000000003',
     '9a1b0c30-0004-4a00-9000-000000000004', 'goldlayer', 1, 1),
    ('9a1b0c31-0004-4a00-9000-000000000004',
     '9a1b0c30-0004-4a00-9000-000000000004', 'tealist', 1, 1),
    ('9a1b0c31-0004-4a00-9000-000000000005',
     '9a1b0c30-0004-4a00-9000-000000000004', 'tea', 1, 1),
    ('9a1b0c31-0004-4a00-9000-000000000006',
     '9a1b0c30-0004-4a00-9000-000000000004', 'login', 1, 1),
    ('9a1b0c31-0004-4a00-9000-000000000007',
     '9a1b0c30-0004-4a00-9000-000000000004', 'ingame', 0, 0),
    ('9a1b0c31-0004-4a00-9000-000000000008',
     '9a1b0c30-0004-4a00-9000-000000000004', 'total', 6, 6),
    -- luckytask
    ('9a1b0c31-0005-4a00-9000-000000000001',
     '9a1b0c30-0005-4a00-9000-000000000005', 'bigwinlost', 1, 1),
    ('9a1b0c31-0005-4a00-9000-000000000002',
     '9a1b0c30-0005-4a00-9000-000000000005', 'goldleave', 1, 1),
    ('9a1b0c31-0005-4a00-9000-000000000003',
     '9a1b0c30-0005-4a00-9000-000000000005', 'goldlayer', 1, 1),
    ('9a1b0c31-0005-4a00-9000-000000000004',
     '9a1b0c30-0005-4a00-9000-000000000005', 'tealist', 1, 1),
    ('9a1b0c31-0005-4a00-9000-000000000005',
     '9a1b0c30-0005-4a00-9000-000000000005', 'ingame', 0, 0),
    ('9a1b0c31-0005-4a00-9000-000000000006',
     '9a1b0c30-0005-4a00-9000-000000000005', 'total', 2, 2),
    -- SxvipAct
    ('9a1b0c31-0006-4a00-9000-000000000001',
     '9a1b0c30-0006-4a00-9000-000000000006', 'bigwinlost', 1, NULL),
    ('9a1b0c31-0006-4a00-9000-000000000002',
     '9a1b0c30-0006-4a00-9000-000000000006', 'bigwinlostMatch', 1, NULL),
    ('9a1b0c31-0006-4a00-9000-000000000003',
     '9a1b0c30-0006-4a00-9000-000000000006', 'goldleave', 1, NULL),
    ('9a1b0c31-0006-4a00-9000-000000000004',
     '9a1b0c30-0006-4a00-9000-000000000006', 'goldlayer', 1, NULL),
    ('9a1b0c31-0006-4a00-9000-000000000005',
     '9a1b0c30-0006-4a00-9000-000000000006', 'tea', 1, NULL),
    ('9a1b0c31-0006-4a00-9000-000000000006',
     '9a1b0c30-0006-4a00-9000-000000000006', 'login', 1, NULL),
    ('9a1b0c31-0006-4a00-9000-000000000007',
     '9a1b0c30-0006-4a00-9000-000000000006', 'total', 6, NULL),
    -- MonthlyCard
    ('9a1b0c31-0007-4a00-9000-000000000001',
     '9a1b0c30-0007-4a00-9000-000000000007', 'toGoldHall', 1, 1),
    ('9a1b0c31-0007-4a00-9000-000000000002',
     '9a1b0c30-0007-4a00-9000-000000000007', 'toGoldChooseRoom', 1, 1),
    ('9a1b0c31-0007-4a00-9000-000000000003',
     '9a1b0c30-0007-4a00-9000-000000000007', 'closeBrokenGiftView', 1, 1),
    ('9a1b0c31-0007-4a00-9000-000000000004',
     '9a1b0c30-0007-4a00-9000-000000000007', 'total', 2, 2),
    -- TimeLoginAct：只在首次进入金币场（toGoldHall）当天弹一次，
    -- 从牌局返回（toGoldChooseRoom）与破产礼包关闭（closeBrokenGiftView）都是 0。
    ('9a1b0c31-0008-4a00-9000-000000000001',
     '9a1b0c30-0008-4a00-9000-000000000008', 'toGoldHall', 1, 1),
    ('9a1b0c31-0008-4a00-9000-000000000002',
     '9a1b0c30-0008-4a00-9000-000000000008', 'toGoldChooseRoom', 0, 0),
    ('9a1b0c31-0008-4a00-9000-000000000003',
     '9a1b0c30-0008-4a00-9000-000000000008', 'closeBrokenGiftView', 0, 0),
    ('9a1b0c31-0008-4a00-9000-000000000004',
     '9a1b0c30-0008-4a00-9000-000000000008', 'total', 1, 1);

-- AllConfig.PopSystem。顶层 {closeCnt=3, cdDay=7} 是原版兜底默认，
-- bindPhone / MonthlyCard / TimeLoginAct 三个覆盖项也照抄观测值。
-- TimeLoginAct 的 cdDay=3000 约合 8 年，等价于「关三次就永久不再自动弹」。
INSERT INTO pop_system_configs (
    id, config_key, close_cnt, cd_day, daily_cnt, created_at, updated_at
) VALUES
    ('9a1b0c32-0000-4a00-9000-000000000001', '__default__', 3, 7, NULL, now(), now()),
    ('9a1b0c32-0000-4a00-9000-000000000002', 'bindPhone', 1, 7, NULL, now(), now()),
    ('9a1b0c32-0000-4a00-9000-000000000003', 'MonthlyCard', 4, 1, 2, now(), now()),
    ('9a1b0c32-0000-4a00-9000-000000000004', 'TimeLoginAct', 3, 3000, NULL, now(), now());
