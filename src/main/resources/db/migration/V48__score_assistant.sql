CREATE TABLE score_ledgers (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    round_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_score_ledger_status CHECK (status IN ('IN_PROGRESS', 'ENDED')),
    CONSTRAINT ck_score_ledger_round_count CHECK (round_count BETWEEN 0 AND 99),
    CONSTRAINT ck_score_ledger_end CHECK (
        (status = 'IN_PROGRESS' AND ended_at IS NULL)
        OR (status = 'ENDED' AND ended_at IS NOT NULL AND ended_at >= started_at))
);

CREATE INDEX idx_score_ledgers_active
    ON score_ledgers(owner_user_id, started_at DESC)
    WHERE status = 'IN_PROGRESS' AND deleted_at IS NULL;

CREATE INDEX idx_score_ledgers_history
    ON score_ledgers(owner_user_id, ended_at DESC, id)
    WHERE status = 'ENDED' AND deleted_at IS NULL;

CREATE TABLE score_ledger_players (
    id UUID PRIMARY KEY,
    ledger_id UUID NOT NULL REFERENCES score_ledgers(id) ON DELETE CASCADE,
    position SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 6),
    display_name VARCHAR(40) NOT NULL,
    owner_player BOOLEAN NOT NULL,
    total_score BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_score_player_name
        CHECK (display_name = BTRIM(display_name) AND display_name <> ''),
    UNIQUE (ledger_id, position),
    UNIQUE (ledger_id, display_name)
);

CREATE UNIQUE INDEX uk_score_ledger_owner_player
    ON score_ledger_players(ledger_id)
    WHERE owner_player = TRUE;

CREATE TABLE score_ledger_rounds (
    id UUID PRIMARY KEY,
    ledger_id UUID NOT NULL REFERENCES score_ledgers(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL CHECK (round_number BETWEEN 1 AND 99),
    recorded_at TIMESTAMPTZ NOT NULL,
    UNIQUE (ledger_id, round_number)
);

CREATE INDEX idx_score_ledger_rounds_time
    ON score_ledger_rounds(ledger_id, recorded_at, round_number);

CREATE TABLE score_ledger_round_scores (
    round_id UUID NOT NULL REFERENCES score_ledger_rounds(id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES score_ledger_players(id) ON DELETE CASCADE,
    score_delta BIGINT NOT NULL,
    total_after BIGINT NOT NULL,
    PRIMARY KEY (round_id, player_id)
);

CREATE INDEX idx_score_round_scores_player
    ON score_ledger_round_scores(player_id, round_id);
