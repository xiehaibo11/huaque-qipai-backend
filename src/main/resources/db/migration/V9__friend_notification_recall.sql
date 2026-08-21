-- 召回通知类型：牌友召回与原版的"召回"页签对齐
ALTER TABLE friend_notifications
    DROP CONSTRAINT ck_friend_notification_type;

ALTER TABLE friend_notifications
    ADD CONSTRAINT ck_friend_notification_type
    CHECK (type IN ('INVITE', 'RESERVE', 'RECALL'));
