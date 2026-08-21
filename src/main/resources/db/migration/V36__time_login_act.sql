-- 定时登录有礼（原版 lobby.Modules.TimeLoginAct）。
--
-- 原版客户端只渲染服务端下发的 loginRewards / goldOver / supplementCnt /
-- wheelReward，本仓库没有原版服务端源码，也没有对应的活动配置文件。
-- 因此下面的目录、数值与解锁阈值都是南北娱乐自建配置，不得描述成恢复了
-- 原版服务端算法。逐条来源见 android/docs/ORIGINAL-TIME-LOGIN-ACT-EVIDENCE.md
-- 第 9 节的未闭合项。

CREATE TABLE time_login_activities (
    id UUID PRIMARY KEY,
    activity_code VARCHAR(64) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- 携带金币超过该值不可领奖；原版 Module.lua:13 的客户端兜底默认值是 50000。
    gold_over BIGINT NOT NULL,
    -- 可向前补领的时段数；原版由服务端 supplementCnt 下发。
    supplement_count INTEGER NOT NULL,
    -- 解锁转盘所需的当日领取次数；原版由 wheelReward[1].wheelCnt 下发。
    wheel_unlock_count INTEGER NOT NULL,
    -- 活动自然日的切分秒；原版 Config.lua:15 的 EVENING = 82800（23:00）。
    day_boundary_second INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (gold_over >= 0),
    CHECK (supplement_count >= 0),
    CHECK (wheel_unlock_count > 0),
    CHECK (day_boundary_second BETWEEN 1 AND 86400)
);

CREATE TABLE time_login_slots (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES time_login_activities(id) ON DELETE RESTRICT,
    slot_order INTEGER NOT NULL,
    -- 当日零点起的秒数，与原版 loginRewards[].startTime/endTime 同语义；
    -- start_second >= end_second 表示跨零点时段（原版早间 23:00-09:00）。
    start_second INTEGER NOT NULL,
    end_second INTEGER NOT NULL,
    reward_type VARCHAR(32) NOT NULL,
    reward_amount BIGINT NOT NULL,
    reward_name VARCHAR(64) NOT NULL,
    UNIQUE (activity_id, slot_order),
    CHECK (slot_order > 0),
    CHECK (start_second BETWEEN 0 AND 86399),
    CHECK (end_second BETWEEN 1 AND 86400),
    CHECK (reward_amount > 0),
    CHECK (reward_type IN ('COIN', 'DIAMOND', 'ROOM_CARD'))
);

CREATE TABLE time_login_wheel_slices (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES time_login_activities(id) ON DELETE RESTRICT,
    -- 与原版 WheelView 的 _KW_ITEM_1.._KW_ITEM_8 一一对应，0 基。
    slice_index INTEGER NOT NULL,
    reward_type VARCHAR(32) NOT NULL,
    reward_amount BIGINT NOT NULL,
    reward_name VARCHAR(64) NOT NULL,
    weight INTEGER NOT NULL,
    UNIQUE (activity_id, slice_index),
    CHECK (slice_index BETWEEN 0 AND 7),
    CHECK (reward_amount > 0),
    CHECK (weight > 0),
    CHECK (reward_type IN ('COIN', 'DIAMOND', 'ROOM_CARD'))
);

CREATE TABLE time_login_claims (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    activity_id UUID NOT NULL REFERENCES time_login_activities(id) ON DELETE RESTRICT,
    -- 活动自然日：按中国时区把 day_boundary_second 之后算入次日。
    activity_date DATE NOT NULL,
    claim_type VARCHAR(16) NOT NULL,
    slot_id UUID REFERENCES time_login_slots(id) ON DELETE RESTRICT,
    wheel_slice_index INTEGER,
    reward_type VARCHAR(32) NOT NULL,
    reward_amount BIGINT NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL,
    CHECK (claim_type IN ('SLOT', 'WHEEL')),
    CHECK (
        (claim_type = 'SLOT' AND slot_id IS NOT NULL AND wheel_slice_index IS NULL)
        OR
        (claim_type = 'WHEEL' AND slot_id IS NULL AND wheel_slice_index BETWEEN 0 AND 7)
    )
);

-- 同一活动自然日内，一个时段只能领一次。
CREATE UNIQUE INDEX uq_time_login_claims_slot
    ON time_login_claims(user_id, activity_id, activity_date, slot_id)
    WHERE claim_type = 'SLOT';

-- 同一活动自然日内，转盘只能抽一次。
CREATE UNIQUE INDEX uq_time_login_claims_wheel
    ON time_login_claims(user_id, activity_id, activity_date)
    WHERE claim_type = 'WHEEL';

CREATE INDEX idx_time_login_claims_day
    ON time_login_claims(user_id, activity_id, activity_date);

CREATE TABLE time_login_operations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, idempotency_key)
);

-- 南北娱乐兼容配置。三段时长、1000/1000/1200 金币、5 万金币上限、
-- 3 次解锁转盘与 10 万金币封顶取自用户提供的 1.5.4 实机截图
-- （仓库根 `image copy 2.png`），属单次线上观测，不是原版配置事实。
-- 原版三张卡底图把「早间/午间/晚间」烤进美术，因此时段固定为三段。
INSERT INTO time_login_activities (
    id, activity_code, enabled, gold_over, supplement_count,
    wheel_unlock_count, day_boundary_second, created_at, updated_at
) VALUES (
    '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10',
    'TIME_LOGIN_DAILY',
    TRUE,
    50000,
    1,
    3,
    82800,
    now(),
    now()
);

INSERT INTO time_login_slots (
    id, activity_id, slot_order, start_second, end_second,
    reward_type, reward_amount, reward_name
) VALUES
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d11',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 1, 82800, 32400, 'COIN', 1000, '金币'),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d12',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 2, 32400, 57600, 'COIN', 1000, '金币'),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d13',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 3, 57600, 82800, 'COIN', 1200, '金币');

INSERT INTO time_login_wheel_slices (
    id, activity_id, slice_index, reward_type, reward_amount, reward_name, weight
) VALUES
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d20',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 0, 'COIN', 2000, '金币', 240),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d21',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 1, 'DIAMOND', 5, '钻石', 120),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d22',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 2, 'COIN', 5000, '金币', 180),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d23',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 3, 'ROOM_CARD', 1, '房卡', 60),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d24',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 4, 'COIN', 10000, '金币', 120),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d25',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 5, 'DIAMOND', 20, '钻石', 40),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d26',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 6, 'COIN', 50000, '金币', 30),
    ('2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d27',
     '2f5a1c40-9b31-4f02-8a6d-2c7f5c1a4d10', 7, 'COIN', 100000, '金币', 10);
