ALTER TABLE player_wallets
    ADD COLUMN bound_room_cards BIGINT NOT NULL DEFAULT 0;

ALTER TABLE player_wallets
    ADD CONSTRAINT ck_player_wallets_bound_room_cards_nonnegative
        CHECK (bound_room_cards >= 0);
