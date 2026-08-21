CREATE TABLE membership_notice_configs (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    version INTEGER NOT NULL CHECK (version > 0),
    title VARCHAR(64) NOT NULL,
    items JSONB NOT NULL,
    change_notice TEXT NOT NULL,
    agreement_title VARCHAR(64) NOT NULL,
    agreement_url VARCHAR(512) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO membership_notice_configs (
    id,
    version,
    title,
    items,
    change_notice,
    agreement_title,
    agreement_url,
    active
) VALUES (
    1,
    1,
    '会员须知',
    '[
      "1.购买会员卡后可立即获得对应会员，并在会员卡时效内获得对应权益。",
      "2.每日领取福利需要在【会员权益】界面手动领取，未领取的福利次日失效。",
      "3.不同种类的会员卡可同时购买，福利独立发放。",
      "4.续费或叠加购买同种会员卡，时效自动顺延。"
    ]'::jsonb,
    '如在您会员卡生效期间，因运营策略调整等原因发生变更，您享受的会员权益在有效期内不会发生改变；如您在生效期间进行续费，续费后的会员卡将调整为新的会员权益。',
    '用户协议',
    'https://www.nanbeiyule.com/terms',
    TRUE
);
