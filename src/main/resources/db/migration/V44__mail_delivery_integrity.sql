ALTER TABLE mails
    ADD COLUMN source_type VARCHAR(80),
    ADD COLUMN source_id VARCHAR(160),
    ADD CONSTRAINT uq_mails_delivery_source
        UNIQUE (user_id, source_type, source_id),
    ADD CONSTRAINT chk_mails_attachments_array
        CHECK (jsonb_typeof(attachments) = 'array'),
    ADD CONSTRAINT chk_mails_expiry_after_send
        CHECK (expire_at IS NULL OR expire_at > send_at);

CREATE INDEX idx_mails_user_visible_page
    ON mails(user_id, send_at DESC, id DESC)
    WHERE deleted_at IS NULL;
