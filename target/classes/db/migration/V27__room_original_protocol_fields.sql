-- Store original create-room tabletype equivalent returned by ReqCreate.tabletype.
ALTER TABLE game_rooms
    ADD COLUMN room_mode INTEGER NOT NULL DEFAULT 0;
