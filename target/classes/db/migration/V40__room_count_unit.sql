-- 30109 原版建房配置：四人档是圈数、两人档是局数。牌局手数换算为南北自建语义。
ALTER TABLE game_rooms
    ADD COLUMN count_unit VARCHAR(16) NOT NULL DEFAULT 'ROUND';

UPDATE game_rooms
SET count_unit = 'CIRCLE'
WHERE venue = 'BOX'
  AND game_id = 30109
  AND player_count = 4;

ALTER TABLE game_rooms
    ADD CONSTRAINT ck_game_rooms_count_unit
        CHECK (count_unit IN ('ROUND', 'CIRCLE'));
