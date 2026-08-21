-- 大厅入口气泡文案：亲友圈引导。
--
-- V25 只加了 bubble_text / bubble_type / bubble_interval_seconds 三列，没有预置任何文案，
-- 因此大厅至今不显示气泡。本迁移写入第一条运营文案，把气泡链路接通。
--
-- 挂在 CREATE_ROOM 是因为找不到亲友圈的玩家最先点的是“创建房间”；三个主入口横向间距
-- 510 设计单位，而本条文案按原版公式 `20 + (UTF-8 字节数 / 3) * 20` 得到 320 原版单位
-- （换算到 3200 宽设计空间为 767.6），因此气泡会向右压到“加入房间”卡片。
-- 挂到更靠右的槽位会溢出页面右缘，所以选最左的主入口。文案缩短到 8 字以内即可完全落在卡内。
--
-- bubble_type = 2 对应 BubbleItem.lua 的 animAction：淡入后停 8 秒再隐藏，循环播放；
-- 3 是 Q 弹序列，4 需要客户端拿到播放次数，当前 Game Home API 不下发。
-- bubble_interval_seconds = 30 是原版 `getBubbleInterval() or 30` 的缺省值。
--
-- 后续改文案不要再加迁移：直接 UPDATE game_home_entries 即可，本迁移只提供初始值。

UPDATE game_home_entries
SET bubble_text = '亲友圈请点击右下角“亲友约局”',
    bubble_type = 2,
    bubble_interval_seconds = 30
WHERE code = 'CREATE_ROOM';
