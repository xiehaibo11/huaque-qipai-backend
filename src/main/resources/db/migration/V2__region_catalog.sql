CREATE TABLE region_cities (
    code VARCHAR(24) PRIMARY KEY,
    name VARCHAR(24) NOT NULL,
    sort_order INTEGER NOT NULL UNIQUE,
    map_x INTEGER NOT NULL CHECK (map_x >= 0),
    map_y INTEGER NOT NULL CHECK (map_y >= 0),
    secondary_map VARCHAR(80),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE region_lobbies (
    lobby_id BIGINT PRIMARY KEY,
    city_code VARCHAR(24) NOT NULL REFERENCES region_cities(code),
    area_name VARCHAR(40) NOT NULL,
    sort_order INTEGER NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL,
    default_lobby BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_region_default_lobby
    ON region_lobbies(default_lobby)
    WHERE default_lobby = TRUE;

CREATE INDEX idx_region_lobby_city
    ON region_lobbies(city_code, sort_order);

CREATE TABLE user_region_selections (
    user_id UUID PRIMARY KEY REFERENCES app_users(id),
    lobby_id BIGINT NOT NULL REFERENCES region_lobbies(lobby_id),
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO region_cities (
    code, name, sort_order, map_x, map_y, secondary_map,
    enabled, created_at, updated_at
) VALUES
    ('huzhou', '湖州', 1, 247, 150, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('jiaxing', '嘉兴', 2, 575, 89, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hangzhou', '杭州', 3, 484, 258, 'second_area_hangzhou', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('shaoxing', '绍兴', 4, 786, 383, 'second_area_shaoxing', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ningbo', '宁波', 5, 901, 296, 'second_area_ningbo', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('zhoushan', '舟山', 6, 765, 161, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('quzhou', '衢州', 7, 253, 490, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('jinhua', '金华', 8, 471, 491, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('taizhou', '台州', 9, 949, 560, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('lishui', '丽水', 10, 438, 661, 'second_area_lishui', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('wenzhou', '温州', 11, 716, 560, 'second_area_wenzhou', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO region_lobbies (
    lobby_id, city_code, area_name, sort_order, enabled, default_lobby,
    created_at, updated_at
) VALUES
    (900038, 'lishui', '丽水', 1, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900037, 'wenzhou', '温州(茶)', 2, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900017, 'wenzhou', '温州(熟)', 3, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900021, 'hangzhou', '杭州', 4, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900023, 'taizhou', '台州', 5, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900043, 'zhoushan', '舟山', 6, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900008, 'huzhou', '湖州', 7, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900003, 'quzhou', '衢州', 8, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900036, 'wenzhou', '瑞安', 9, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900020, 'jinhua', '金华', 10, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900025, 'hangzhou', '杭州(宝宝)', 11, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900031, 'wenzhou', '乐清', 12, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900046, 'shaoxing', '绍兴麻将', 13, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900007, 'shaoxing', '嵊州(越)', 14, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900039, 'lishui', '青田', 15, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900006, 'ningbo', '宁波', 16, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (40165, 'jiaxing', '嘉兴', 17, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (900029, 'ningbo', '余姚', 18, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
