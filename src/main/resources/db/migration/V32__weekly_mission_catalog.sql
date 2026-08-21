-- 每周任务页此前只有页面记录、没有任务和活跃度阶段，客户端打开就是原版的「暂无可完成任务~」空态。
-- 浙江游戏大厅的任务内容由服务端 pageList/boxList 下发，仓库内没有原版每周任务配置，
-- 因此这里的目标值、活跃值和奖励是南北娱乐自建后端的配置数据，不冒充原版事实；
-- 页面结构、周期、活跃度阶段和领奖语义仍与 LuckyMission 的客户端行为一致。
--
-- 不使用 LOGIN 事件：LOGIN 目前只在读取每日任务页时按周期幂等记 1，
-- 周周期下没有「每天记一次」的去重，放进每周任务会变成一周只记一次，语义不对。

INSERT INTO mission_task_definitions(
    task_code, page_code, event_type, display_name, target,
    activity_points, jump_type, display_order, enabled
) VALUES
    ('WEEKLY_GAME_COMPLETED_20', 'WEEKLY', 'GAME_COMPLETED', '本周完成任意对局20局', 20, 600, 'GAME_HOME', 10, TRUE),
    ('WEEKLY_GAME_WON_10', 'WEEKLY', 'GAME_WON', '本周任意对局胜利10局', 10, 600, 'GAME_HOME', 20, TRUE),
    ('WEEKLY_INTERACTIVE_PROP_10', 'WEEKLY', 'INTERACTIVE_PROP_USED', '本周累计使用互动道具10次', 10, 400, 'INTERACTIVE_PROP', 30, TRUE);

INSERT INTO mission_milestone_definitions(id, page_code, target, display_order, enabled) VALUES
    ('31000000-0000-4000-8000-000000000400', 'WEEKLY', 400, 10, TRUE),
    ('31000000-0000-4000-8000-000000000800', 'WEEKLY', 800, 20, TRUE),
    ('31000000-0000-4000-8000-000000001200', 'WEEKLY', 1200, 30, TRUE),
    ('31000000-0000-4000-8000-000000001600', 'WEEKLY', 1600, 40, TRUE);

INSERT INTO mission_rewards(
    id, task_code, page_code, milestone_target, reward_order,
    reward_type, item_code, display_name, icon_key, amount
) VALUES
    ('31100000-0000-4000-8000-000000000001', 'WEEKLY_GAME_COMPLETED_20', NULL, NULL, 10, 'COIN', 'COIN', '金币', 'mission_coin', 1000),
    ('31100000-0000-4000-8000-000000000002', 'WEEKLY_GAME_WON_10', NULL, NULL, 10, 'COIN', 'COIN', '金币', 'mission_coin', 800),
    ('31100000-0000-4000-8000-000000000003', 'WEEKLY_GAME_WON_10', NULL, NULL, 20, 'INVENTORY', 'DOUBLE_SCORE_CARD', '加倍卡', 'mission_double_card', 1),
    ('31100000-0000-4000-8000-000000000004', 'WEEKLY_INTERACTIVE_PROP_10', NULL, NULL, 10, 'COIN', 'COIN', '金币', 'mission_coin', 500),
    ('31200000-0000-4000-8000-000000000400', NULL, 'WEEKLY', 400, 10, 'COIN', 'COIN', '金币', 'mission_coin', 1000),
    ('31200000-0000-4000-8000-000000000800', NULL, 'WEEKLY', 800, 10, 'COIN', 'COIN', '金币', 'mission_coin', 2000),
    ('31200000-0000-4000-8000-000000001200', NULL, 'WEEKLY', 1200, 10, 'COIN', 'COIN', '金币', 'mission_coin', 3000),
    ('31200000-0000-4000-8000-000000001600', NULL, 'WEEKLY', 1600, 10, 'ROOM_CARD', 'ROOM_CARD', '房卡', 'mission_room_card', 5);
