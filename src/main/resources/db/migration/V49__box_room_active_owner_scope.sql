DROP INDEX IF EXISTS uk_game_rooms_owner_open;

CREATE UNIQUE INDEX uk_game_rooms_owner_open
    ON game_rooms (owner_user_id)
    WHERE status <> 'DISSOLVED' AND venue = 'BOX';
