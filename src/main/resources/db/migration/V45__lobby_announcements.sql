CREATE TABLE lobby_announcements (
    id BIGSERIAL PRIMARY KEY,
    content VARCHAR(500) NOT NULL,
    lobby_id BIGINT REFERENCES region_lobbies(lobby_id) ON DELETE RESTRICT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_lobby_announcement_content
        CHECK (content = BTRIM(content) AND content <> ''),
    CONSTRAINT ck_lobby_announcement_time_range
        CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX idx_lobby_announcements_delivery
    ON lobby_announcements(enabled, sort_order, id);

INSERT INTO lobby_announcements (content, sort_order)
VALUES ('游戏公告:适当游戏益脑，沉迷游戏伤身', 10);
