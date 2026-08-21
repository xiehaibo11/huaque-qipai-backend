-- 大厅入口气泡配置。
--
-- 对应闲逸斗地主原版游戏卡片的 hall_tip_type_2 节点：
-- prime/hall/style/HallStyle3Layer.lua:1400-1414 用 data:getBubbleText() 判空后
-- 创建 BubbleItem，并读取 getBubbleType() 与 getBubbleInterval()（原版缺省 30 秒）。
--
-- 三列全部可空，纯加列，不改变已发布 JSON 字段含义：为空即该入口不展示气泡，
-- 与本次变更前的行为一致。文案由运营写入，不在迁移里预置任何促销内容。

-- bubble_type 用 INTEGER 而不是 SMALLINT：实体字段是 Integer，
-- Hibernate ddl-auto=validate 会把 int2 判为类型不符。
ALTER TABLE game_home_entries
    ADD COLUMN bubble_text VARCHAR(120),
    ADD COLUMN bubble_type INTEGER,
    ADD COLUMN bubble_interval_seconds INTEGER;

-- 原版只实现了 2、3、4 三种播放类型，其余值一律不展示气泡。
ALTER TABLE game_home_entries
    ADD CONSTRAINT chk_game_home_entries_bubble_type
        CHECK (bubble_type IS NULL OR bubble_type IN (2, 3, 4));

ALTER TABLE game_home_entries
    ADD CONSTRAINT chk_game_home_entries_bubble_interval
        CHECK (bubble_interval_seconds IS NULL OR bubble_interval_seconds > 0);
