CREATE TABLE player_avatars (
    avatar_key VARCHAR(120) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    content_type VARCHAR(40) NOT NULL,
    image_bytes BYTEA NOT NULL,
    byte_size INTEGER NOT NULL CHECK (byte_size > 0),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_player_avatars_sha256_user
    ON player_avatars(user_id, sha256);
