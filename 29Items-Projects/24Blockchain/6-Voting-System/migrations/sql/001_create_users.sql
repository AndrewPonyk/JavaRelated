-- Off-chain user profile cache
-- Links Ethereum wallet addresses to display names and preferences

CREATE TABLE IF NOT EXISTS users (
    id              SERIAL PRIMARY KEY,
    wallet_address  VARCHAR(42) NOT NULL UNIQUE,  -- Ethereum address (0x + 40 hex chars)
    display_name    VARCHAR(100),
    avatar_ipfs_cid VARCHAR(100),                  -- IPFS CID of avatar image
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_users_wallet ON users(wallet_address);

-- Nonce table for signature-based authentication
CREATE TABLE IF NOT EXISTS auth_nonces (
    id              SERIAL PRIMARY KEY,
    wallet_address  VARCHAR(42) NOT NULL REFERENCES users(wallet_address),
    nonce           VARCHAR(100) NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    used            BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_auth_nonces_wallet ON auth_nonces(wallet_address);
