CREATE TABLE mission_pages (
    page_code VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    cycle_type VARCHAR(16) NOT NULL,
    display_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (cycle_type IN ('DAILY', 'WEEKLY'))
);

CREATE TABLE mission_task_definitions (
    task_code VARCHAR(64) PRIMARY KEY,
    page_code VARCHAR(32) NOT NULL REFERENCES mission_pages(page_code) ON DELETE RESTRICT,
    event_type VARCHAR(48) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    target BIGINT NOT NULL,
    activity_points BIGINT NOT NULL,
    jump_type VARCHAR(48) NOT NULL,
    display_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (target > 0),
    CHECK (activity_points >= 0)
);

CREATE INDEX idx_mission_tasks_page_order
    ON mission_task_definitions(page_code, enabled, display_order);
CREATE INDEX idx_mission_tasks_event
    ON mission_task_definitions(event_type, enabled);

CREATE TABLE mission_milestone_definitions (
    id UUID PRIMARY KEY,
    page_code VARCHAR(32) NOT NULL REFERENCES mission_pages(page_code) ON DELETE RESTRICT,
    target BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (page_code, target),
    CHECK (target > 0)
);

CREATE TABLE mission_rewards (
    id UUID PRIMARY KEY,
    task_code VARCHAR(64) REFERENCES mission_task_definitions(task_code) ON DELETE CASCADE,
    page_code VARCHAR(32),
    milestone_target BIGINT,
    reward_order INTEGER NOT NULL,
    reward_type VARCHAR(32) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    icon_key VARCHAR(64) NOT NULL,
    amount BIGINT NOT NULL,
    CHECK (amount > 0),
    CHECK (reward_type IN ('COIN', 'DIAMOND', 'ROOM_CARD', 'COUPON', 'INVENTORY')),
    CHECK (
        (task_code IS NOT NULL AND page_code IS NULL AND milestone_target IS NULL)
        OR
        (task_code IS NULL AND page_code IS NOT NULL AND milestone_target IS NOT NULL)
    ),
    FOREIGN KEY (page_code, milestone_target)
        REFERENCES mission_milestone_definitions(page_code, target) ON DELETE CASCADE
);

CREATE INDEX idx_mission_rewards_task ON mission_rewards(task_code, reward_order);
CREATE INDEX idx_mission_rewards_milestone
    ON mission_rewards(page_code, milestone_target, reward_order);

CREATE TABLE user_mission_progress (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    task_code VARCHAR(64) NOT NULL REFERENCES mission_task_definitions(task_code) ON DELETE RESTRICT,
    cycle_started_at TIMESTAMPTZ NOT NULL,
    target BIGINT NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    claimed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (user_id, task_code, cycle_started_at),
    CHECK (target > 0),
    CHECK (progress >= 0 AND progress <= target)
);

CREATE INDEX idx_user_mission_progress_cycle
    ON user_mission_progress(user_id, cycle_started_at, task_code);

CREATE TABLE mission_milestone_claims (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    page_code VARCHAR(32) NOT NULL,
    cycle_started_at TIMESTAMPTZ NOT NULL,
    target BIGINT NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, page_code, cycle_started_at, target),
    FOREIGN KEY (page_code, target)
        REFERENCES mission_milestone_definitions(page_code, target) ON DELETE RESTRICT
);

CREATE TABLE mission_claim_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    claim_type VARCHAR(16) NOT NULL,
    claim_reference VARCHAR(128) NOT NULL,
    response_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    UNIQUE (user_id, idempotency_key),
    CHECK (claim_type IN ('TASK', 'MILESTONE'))
);

INSERT INTO mission_pages(page_code, display_name, cycle_type, display_order, enabled) VALUES
    ('DAILY', '每日任务', 'DAILY', 10, TRUE),
    ('WEEKLY', '每周任务', 'WEEKLY', 20, TRUE);

INSERT INTO mission_task_definitions(
    task_code, page_code, event_type, display_name, target,
    activity_points, jump_type, display_order, enabled
) VALUES
    ('DAILY_LOGIN', 'DAILY', 'LOGIN', '每日登录奖励', 1, 400, 'LOGIN', 10, TRUE),
    ('DAILY_INTERACTIVE_PROP_2', 'DAILY', 'INTERACTIVE_PROP_USED', '累计使用互动道具', 2, 200, 'INTERACTIVE_PROP', 20, TRUE),
    ('DAILY_GAME_COMPLETED_3', 'DAILY', 'GAME_COMPLETED', '完成任意对局3局', 3, 400, 'GAME_HOME', 30, TRUE),
    ('DAILY_GAME_WON_1', 'DAILY', 'GAME_WON', '任意对局胜利1局', 1, 400, 'GAME_HOME', 40, TRUE);

INSERT INTO mission_milestone_definitions(id, page_code, target, display_order, enabled) VALUES
    ('22000000-0000-4000-8000-000000000800', 'DAILY', 800, 10, TRUE),
    ('22000000-0000-4000-8000-000000001200', 'DAILY', 1200, 20, TRUE),
    ('22000000-0000-4000-8000-000000001600', 'DAILY', 1600, 30, TRUE),
    ('22000000-0000-4000-8000-000000002000', 'DAILY', 2000, 40, TRUE);

INSERT INTO mission_rewards(
    id, task_code, page_code, milestone_target, reward_order,
    reward_type, item_code, display_name, icon_key, amount
) VALUES
    ('22100000-0000-4000-8000-000000000001', 'DAILY_LOGIN', NULL, NULL, 10, 'COIN', 'COIN', '金币', 'mission_coin', 300),
    ('22100000-0000-4000-8000-000000000002', 'DAILY_INTERACTIVE_PROP_2', NULL, NULL, 10, 'COIN', 'COIN', '金币', 'mission_coin', 300),
    ('22100000-0000-4000-8000-000000000003', 'DAILY_GAME_COMPLETED_3', NULL, NULL, 10, 'COIN', 'COIN', '金币', 'mission_coin', 200),
    ('22100000-0000-4000-8000-000000000004', 'DAILY_GAME_COMPLETED_3', NULL, NULL, 20, 'INVENTORY', 'DOUBLE_SCORE_CARD', '加倍卡', 'mission_double_card', 1),
    ('22100000-0000-4000-8000-000000000005', 'DAILY_GAME_WON_1', NULL, NULL, 10, 'COIN', 'COIN', '金币', 'mission_coin', 200),
    ('22200000-0000-4000-8000-000000000800', NULL, 'DAILY', 800, 10, 'COIN', 'COIN', '金币', 'mission_coin', 500),
    ('22200000-0000-4000-8000-000000001200', NULL, 'DAILY', 1200, 10, 'COIN', 'COIN', '金币', 'mission_coin', 1000),
    ('22200000-0000-4000-8000-000000001600', NULL, 'DAILY', 1600, 10, 'COIN', 'COIN', '金币', 'mission_coin', 1500),
    ('22200000-0000-4000-8000-000000002000', NULL, 'DAILY', 2000, 10, 'COIN', 'COIN', '金币', 'mission_coin', 2000);
