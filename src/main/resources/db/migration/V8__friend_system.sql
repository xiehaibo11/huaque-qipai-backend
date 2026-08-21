ALTER TABLE app_users
    ADD COLUMN last_active_at TIMESTAMPTZ;

CREATE TABLE friendships (
    user_id UUID NOT NULL REFERENCES app_users(id),
    friend_id UUID NOT NULL REFERENCES app_users(id),
    shielded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, friend_id)
);

CREATE INDEX idx_friendships_friend
    ON friendships(friend_id);

CREATE TABLE friend_applications (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES app_users(id),
    target_id UUID NOT NULL REFERENCES app_users(id),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    handled_at TIMESTAMPTZ,
    CONSTRAINT ck_friend_application_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE UNIQUE INDEX uk_friend_application_pending
    ON friend_applications(requester_id, target_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_friend_applications_target
    ON friend_applications(target_id, status);

CREATE TABLE friend_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    actor_id UUID NOT NULL REFERENCES app_users(id),
    type VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    CONSTRAINT ck_friend_notification_type
        CHECK (type IN ('INVITE', 'RESERVE'))
);

CREATE INDEX idx_friend_notifications_user
    ON friend_notifications(user_id, read_at);
