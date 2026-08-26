ALTER TABLE lobby_announcements
    ADD COLUMN title VARCHAR(200),
    ADD COLUMN subtitle VARCHAR(300),
    ADD COLUMN body_text TEXT,
    ADD COLUMN page_url VARCHAR(2048),
    ADD COLUMN version BIGINT;

UPDATE lobby_announcements
SET title = LEFT(content, 200),
    subtitle = '',
    body_text = content,
    version = 1;

ALTER TABLE lobby_announcements
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN subtitle SET NOT NULL,
    ALTER COLUMN subtitle SET DEFAULT '',
    ALTER COLUMN version SET NOT NULL,
    ALTER COLUMN version SET DEFAULT 1,
    ADD CONSTRAINT ck_lobby_announcement_title
        CHECK (title = BTRIM(title) AND title <> ''),
    ADD CONSTRAINT ck_lobby_announcement_subtitle
        CHECK (subtitle = BTRIM(subtitle)),
    ADD CONSTRAINT ck_lobby_announcement_content_mode
        CHECK ((body_text IS NOT NULL AND page_url IS NULL)
            OR (body_text IS NULL AND page_url IS NOT NULL)),
    ADD CONSTRAINT ck_lobby_announcement_body
        CHECK (body_text IS NULL OR (body_text = BTRIM(body_text) AND body_text <> '')),
    ADD CONSTRAINT ck_lobby_announcement_page_url
        CHECK (page_url IS NULL OR (page_url = BTRIM(page_url)
            AND page_url LIKE 'https://%')),
    ADD CONSTRAINT ck_lobby_announcement_version
        CHECK (version > 0);

CREATE TABLE announcement_reads (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    announcement_id BIGINT NOT NULL REFERENCES lobby_announcements(id) ON DELETE CASCADE,
    announcement_version BIGINT NOT NULL CHECK (announcement_version > 0),
    read_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, announcement_id)
);

CREATE INDEX idx_announcement_reads_announcement
    ON announcement_reads(announcement_id);
