-- Off-chain vote audit trail
-- Caches vote events from blockchain for fast querying

CREATE TABLE IF NOT EXISTS vote_commits (
    id              SERIAL PRIMARY KEY,
    proposal_id     INTEGER NOT NULL REFERENCES proposals(id),
    voter_address   VARCHAR(42) NOT NULL,
    commit_hash     VARCHAR(66),                     -- bytes32 hex
    tx_hash         VARCHAR(66) NOT NULL,            -- Transaction hash
    block_number    BIGINT NOT NULL,
    committed_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(proposal_id, voter_address)
);

CREATE INDEX idx_vote_commits_proposal ON vote_commits(proposal_id);

CREATE TABLE IF NOT EXISTS vote_reveals (
    id              SERIAL PRIMARY KEY,
    proposal_id     INTEGER NOT NULL REFERENCES proposals(id),
    voter_address   VARCHAR(42) NOT NULL,
    choice          INTEGER NOT NULL,
    tx_hash         VARCHAR(66) NOT NULL,
    block_number    BIGINT NOT NULL,
    revealed_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(proposal_id, voter_address)
);

CREATE INDEX idx_vote_reveals_proposal ON vote_reveals(proposal_id);

-- Delegation events cache
CREATE TABLE IF NOT EXISTS delegation_events (
    id              SERIAL PRIMARY KEY,
    from_address    VARCHAR(42) NOT NULL,
    to_address      VARCHAR(42) NOT NULL,
    proposal_id     INTEGER DEFAULT 0,              -- 0 = global delegation
    event_type      VARCHAR(20) NOT NULL,           -- 'delegated' or 'revoked'
    tx_hash         VARCHAR(66) NOT NULL,
    block_number    BIGINT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_delegation_from ON delegation_events(from_address);
CREATE INDEX idx_delegation_to ON delegation_events(to_address);

-- Block sync tracker — stores the last processed block for event replay
CREATE TABLE IF NOT EXISTS sync_state (
    id              SERIAL PRIMARY KEY,
    contract_name   VARCHAR(100) NOT NULL UNIQUE,
    last_block      BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
