-- 浙江游戏大厅 LuckyMission 的限时任务：任务自身的起止时间可以与所属页签周期不同。
-- View.lua 在 任务 endTime 与页签 endTime 不一致时显示 KW_LEFT_TIME 角标和「剩余:」倒计时，
-- 未到 startTime 时改显示「距离开始:」；drawDeadline 是活动结束后仍可领奖的宽限截止。
-- 三列均可空，空表示该任务完全跟随页签周期，保持既有任务行为不变。

ALTER TABLE mission_task_definitions
    ADD COLUMN starts_at TIMESTAMPTZ,
    ADD COLUMN ends_at TIMESTAMPTZ,
    ADD COLUMN draw_deadline TIMESTAMPTZ;

ALTER TABLE mission_task_definitions
    ADD CONSTRAINT chk_mission_task_window
        CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at),
    ADD CONSTRAINT chk_mission_task_draw_deadline
        CHECK (draw_deadline IS NULL OR ends_at IS NULL OR draw_deadline >= ends_at);
