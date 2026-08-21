CREATE TABLE mails (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    title VARCHAR(200) NOT NULL,
    intro VARCHAR(500) NOT NULL DEFAULT '',
    content TEXT NOT NULL DEFAULT '',
    sender VARCHAR(100) NOT NULL DEFAULT '',
    attachments JSONB NOT NULL DEFAULT '[]'::jsonb,
    send_at TIMESTAMPTZ NOT NULL,
    expire_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mails_user_send_at
    ON mails(user_id, send_at DESC);

CREATE INDEX idx_mails_user_unread
    ON mails(user_id)
    WHERE deleted_at IS NULL AND read_at IS NULL;
