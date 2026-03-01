-- Off-chain proposal metadata cache
-- Mirrors on-chain proposal data for fast querying + stores additional metadata

CREATE TABLE IF NOT EXISTS proposals (
    id                  SERIAL PRIMARY KEY,
    chain_proposal_id   INTEGER UNIQUE,                -- On-chain proposal ID
    contract_address    VARCHAR(42),                    -- Which contract this belongs to
    title               VARCHAR(500) NOT NULL,
    description_cid     VARCHAR(100),                   -- IPFS CID of full proposal document
    proposer_address    VARCHAR(42) NOT NULL,
    phase               VARCHAR(20) NOT NULL DEFAULT 'draft',  -- draft, discussion, voting, tallied, cancelled
    option_count        INTEGER NOT NULL DEFAULT 2,
    commit_deadline     BIGINT,                         -- Block number
    reveal_deadline     BIGINT,                         -- Block number
    discussion_deadline TIMESTAMP WITH TIME ZONE,
    total_votes         INTEGER DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    synced_at           TIMESTAMP WITH TIME ZONE        -- Last time synced from chain
);

CREATE INDEX idx_proposals_phase ON proposals(phase);
CREATE INDEX idx_proposals_proposer ON proposals(proposer_address);
CREATE INDEX idx_proposals_chain_id ON proposals(chain_proposal_id);

-- Proposal options (e.g., "Yes", "No", "Abstain")
CREATE TABLE IF NOT EXISTS proposal_options (
    id              SERIAL PRIMARY KEY,
    proposal_id     INTEGER NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    option_index    INTEGER NOT NULL,
    label           VARCHAR(200) NOT NULL,
    vote_count      INTEGER DEFAULT 0,
    UNIQUE(proposal_id, option_index)
);

-- Amendments cache
CREATE TABLE IF NOT EXISTS amendments (
    id              SERIAL PRIMARY KEY,
    proposal_id     INTEGER NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    author_address  VARCHAR(42) NOT NULL,
    content_cid     VARCHAR(100) NOT NULL,            -- IPFS CID
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_amendments_proposal ON amendments(proposal_id);
