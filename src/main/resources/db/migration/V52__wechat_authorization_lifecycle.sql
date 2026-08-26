ALTER TABLE app_users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN display_name_source VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';

UPDATE app_users AS users
SET display_name_source = 'WECHAT'
WHERE EXISTS (
    SELECT 1
    FROM user_identities AS identities
    WHERE identities.user_id = users.id
      AND identities.provider = 'WECHAT'
);

ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_display_name_source
        CHECK (display_name_source IN ('SYSTEM', 'WECHAT', 'USER'));

ALTER TABLE player_profiles
    ADD COLUMN avatar_source VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';

UPDATE player_profiles
SET avatar_source = 'USER'
WHERE avatar_key <> 'avatar_default';

ALTER TABLE player_profiles
    ADD CONSTRAINT ck_player_profiles_avatar_source
        CHECK (avatar_source IN ('SYSTEM', 'WECHAT', 'USER'));

CREATE TABLE wechat_push_events (
    id UUID PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);
