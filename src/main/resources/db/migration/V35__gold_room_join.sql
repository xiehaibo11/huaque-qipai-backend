-- 金币场进房/匹配幂等流水。
--
-- 原版 50 模式 join 成功后进入 DISPATCH_QUEUE 匹配等待态；本表只记录南北娱乐
-- 现代兼容服务端的 join/matching 接口结果，不创建 game_sessions，也不表示
-- 洗牌、发牌、摸打、动作或结算已经闭合。

CREATE TABLE gold_room_join_operations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    lobby_id BIGINT NOT NULL,
    game_id BIGINT NOT NULL,
    room_name_flag INTEGER NOT NULL,
    result JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_gold_room_join_operations_user_created
    ON gold_room_join_operations(user_id, created_at DESC);
