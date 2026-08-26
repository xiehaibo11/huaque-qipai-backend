ALTER TABLE user_privacy_settings
    ADD COLUMN clipboard_access_enabled BOOLEAN NOT NULL DEFAULT TRUE;
