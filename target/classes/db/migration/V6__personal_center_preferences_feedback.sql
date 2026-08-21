CREATE TABLE user_privacy_settings (
    user_id UUID PRIMARY KEY REFERENCES app_users(id),
    allow_friend_requests BOOLEAN NOT NULL DEFAULT TRUE,
    show_game_record BOOLEAN NOT NULL DEFAULT TRUE,
    show_online_status BOOLEAN NOT NULL DEFAULT TRUE,
    chat_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    personalized_recommendations BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    category VARCHAR(20) NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_user_feedback_category
        CHECK (category IN ('FEEDBACK', 'REPORT')),
    CONSTRAINT ck_user_feedback_status
        CHECK (status IN ('SUBMITTED'))
);

CREATE INDEX idx_user_feedback_user_created
    ON user_feedback(user_id, created_at DESC);
