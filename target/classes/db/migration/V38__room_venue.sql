-- 把「私人包厢」和「金币场」在房间表上彻底分开。
--
-- 原版把两者实现为互不相干的两套系统：包厢走 roomManager / CreateBoxRoomView /
-- JoinBoxRoomView（roomrule 里 roommode=10），金币场走 GoldNew -> Gold 模块的
-- reqJoinGoldRoom（lobby/Modules/GoldNew/Module.lua:424 joinGoldRoomFirst）。
-- 大厅的「创建房间 / 返回房间」两态与建房冲突判定只看包厢
-- （lobby/Modules/Lobby/View.lua:725、:794、:863 的 position.gameID）。
--
-- 南北娱乐此前的金币场撮合借用了包厢引擎建 30109 房卡房并把玩家写进 room_participants，
-- 导致点大厅台州麻将（30400 金币场）之后玩家被判定为「在私人房间里」，大厅翻成返回房间、
-- 创建/加入房间被闸门拦住。加一列显式场所，placement 只认包厢。

ALTER TABLE game_rooms
    ADD COLUMN venue VARCHAR(16) NOT NULL DEFAULT 'BOX';

ALTER TABLE game_rooms
    ADD CONSTRAINT game_rooms_venue_check CHECK (venue IN ('BOX', 'GOLD'));

-- 回填：已有的金币场撮合房由 QaGoldRoomAutoMatchService 建立，标记是
-- creation_request_hash = 'qa-gold-match'。它们不是私人包厢。
UPDATE game_rooms
SET venue = 'GOLD'
WHERE creation_request_hash = 'qa-gold-match';

CREATE INDEX idx_game_rooms_venue_status ON game_rooms (venue, status);
