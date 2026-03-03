# <span style="color:red">Voting System</span>

Decentralized governance platform with anonymous voting via zero-knowledge proofs (zkSNARKs), commit-reveal scheme to prevent vote manipulation, and delegation for representative voting.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Smart Contracts | Solidity 0.8.19, OpenZeppelin |
| Blockchain Dev | Hardhat |
| Frontend | React 18, Web3.js, Metamask |
| Backend API | Node.js, Express |
| Database | PostgreSQL |
| Storage | IPFS |
| Security | MythX, Solhint |
| CI/CD | GitHub Actions |
| Zero-Knowledge | Circom, snarkjs (Groth16) |

## Quick Start

### Prerequisites

- Node.js 18+
- Docker & Docker Compose
- Metamask browser extension

### 1. Start infrastructure

```bash
docker-compose up -d
```

This starts:
- **Hardhat Node** (local blockchain) on port 8545
- **PostgreSQL** on port 5432
- **IPFS** node on ports 5001/8080

### 2. Install dependencies

```bash
npm install
```

### 3. Compile and deploy contracts

```bash
npm run compile
npm run deploy:localhost
```

### 4. Run database migrations

```bash
npm run db:migrate
```

Or connect manually:
```bash
psql postgresql://postgres:postgres@localhost:5432/voting_system -f migrations/sql/001_create_users.sql
psql postgresql://postgres:postgres@localhost:5432/voting_system -f migrations/sql/002_create_proposals.sql
psql postgresql://postgres:postgres@localhost:5432/voting_system -f migrations/sql/003_create_vote_records.sql
```

### 5. Seed test data (optional)

```bash
npm run seed
```

### 6. Start the application

```bash
# Terminal 1: Backend API
npm run server:dev

# Terminal 2: Frontend
npm start
```

- Frontend: http://localhost:3000
- API: http://localhost:3001
- Health check: http://localhost:3001/health

### 7. Connect Metamask

1. Open Metamask
2. Add network: RPC URL `http://127.0.0.1:8545`, Chain ID `1337`
3. Import a Hardhat account (use a private key from `npx hardhat node` output)

## Testing

### Smart Contract Tests

```bash
npm run test:contracts
```

Runs Hardhat tests against an in-process Hardhat Network. Covers:
- VotingSystem: proposal creation, commit-reveal lifecycle, delegation enforcement, access control
- ProposalManager: submission, amendments, phase transitions, member roles
- DelegationRegistry: delegate/revoke, circular prevention, voting power calculation
- E2E: Full voting lifecycle from proposal to tally

### API Tests

```bash
# Requires PostgreSQL running
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/voting_system npm run test:api
```

Covers:
- Proposal CRUD with authentication
- Vote results and commit counts
- Delegation info and voting power
- Health check and error handling

### All Tests

```bash
npm test
```

## API Endpoints

### Auth
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/nonce` | No | Get nonce for wallet signature |
| GET | `/api/auth/me` | Yes | Get user profile |
| PUT | `/api/auth/me` | Yes | Update display name |

### Proposals
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/proposals` | No | List proposals (?phase=&page=&limit=) |
| GET | `/api/proposals/:id` | No | Get proposal with options & amendments |
| POST | `/api/proposals` | Yes | Create draft proposal |
| PUT | `/api/proposals/:id` | Yes | Update draft (proposer only) |
| DELETE | `/api/proposals/:id` | Yes | Delete draft (proposer only) |
| POST | `/api/proposals/:id/amendments` | Yes | Add amendment |

### Voting
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/proposals/:id/results` | No | Get vote tallies |
| GET | `/api/proposals/:id/commits` | No | Get commit count |
| GET | `/api/proposals/:id/voters` | No | List committed voters |
| GET | `/api/proposals/:id/status` | No | Get voting status + on-chain data |

### Delegation
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/delegation/:address` | No | Get delegation info |
| GET | `/api/delegation/:address/power` | No | Get voting power |
| GET | `/api/delegation/:address/history` | No | Get delegation history |

### Authentication

Wallet-based auth using Ethereum signatures:

1. `POST /api/auth/nonce` with `{ walletAddress }` to get a nonce
2. Sign the message with Metamask: `personal_sign(message, account)`
3. Include headers on authenticated requests:
   - `x-wallet-address`: your address
   - `x-signature`: the signed message
   - `x-nonce`: the nonce

## Smart Contracts

### VotingSystem.sol
Commit-reveal voting with optional zkSNARK verification.
- `createProposal(ipfsCid, commitDuration, revealDuration, optionCount)`
- `commitVote(proposalId, commitHash)` — during commit phase
- `revealVoteSimple(proposalId, choice, secret)` — during reveal phase
- `tallyVotes(proposalId)` — after reveal phase ends
- Enforces delegation (voters who delegated cannot vote directly)

### ProposalManager.sol
Proposal lifecycle with discussion and amendment phases.
- `submitProposal(title, ipfsCid, discussionDuration)`
- `addAmendment(proposalId, ipfsCid)` — during discussion
- `advanceToVoting(proposalId)` — after discussion ends
- `closeProposal(proposalId)` — by proposer or admin

### DelegationRegistry.sol
Vote delegation with per-proposal or global scope.
- `delegateVote(delegate, proposalId)` — proposalId=0 for global
- `revokeDelegation(proposalId)`
- `getEffectiveDelegate(voter, proposalId)` — proposal-specific > global
- `getVotingPower(delegate)` — 1 + delegator count

## How Voting Works

### Data Storage

| Data | Where | Why |
|------|-------|-----|
| Title, options, author | PostgreSQL | Fast queries, filtering, pagination |
| Full proposal description | IPFS | Cheap off-chain storage; CID hash guarantees integrity |
| Vote hashes (commits) | Blockchain | Immutable, tamper-proof |
| Revealed votes + tallies | Blockchain | Trustless verification |
| Cached results | PostgreSQL | Synced from chain events for fast reads |

### Voting Flow

```
1. CREATE PROPOSAL (draft)
   User ──POST /api/proposals──> PostgreSQL + IPFS
   (nothing on-chain yet)

2. ADVANCE TO VOTING
   User ──MetaMask tx──> VotingSystem.createProposal() ──> on-chain proposal
        ──POST /advance/complete──> PostgreSQL: saves chain_proposal_id, phase='commit'

3. COMMIT VOTE (hide your choice)
   choice + random secret ──keccak256──> hash
   User ──MetaMask tx──> VotingSystem.commitVote(proposalId, hash)
   On-chain stores ONLY the hash — nobody knows your choice

4. REVEAL VOTE (prove your choice)
   User sends original choice + secret
   Contract checks: keccak256(choice + secret) == stored hash?
   Match ──> vote counted in tally[choice]++

5. TALLY
   Anyone ──> VotingSystem.tallyVotes(proposalId)
   Results become readable via getResult()
```

### Commit-Reveal Scheme

**Problem:** If votes are public immediately, later voters see results and are influenced.

**Solution:** Two phases:
- **Commit phase** — submit a hash of your vote (sealed envelope)
- **Reveal phase** — open the envelope, contract verifies the hash matches

You can't change your vote after committing because the hash won't match.

### Deadlines

Deadlines use **block numbers** (via `block.number` in Solidity):

```
Created at block #7, commitDuration=5, revealDuration=5

Commit phase:  block 7 ──── block 12    (commitVote allowed)
Reveal phase:  block 13 ─── block 17    (revealVote allowed)
Tally:         block 18+                 (tallyVotes allowed)
```

```solidity
// VotingSystem.sol:118-119
p.commitDeadline = block.number + _commitDuration;
p.revealDeadline = block.number + _commitDuration + _revealDuration;
```

### Production vs Local Development

On real Ethereum, new blocks are produced automatically every ~12 seconds by the global network. Deadlines pass naturally with time — no intervention needed.

On Hardhat (local dev chain), **you are the only user** — blocks are only mined when you send a transaction. Between transactions, the chain is frozen: no new blocks, no time passing. A deadline of 2400 blocks would never arrive on its own.

| Environment | Block production | 8-hour vote deadline | Action needed? |
|---|---|---|---|
| Ethereum mainnet | ~12 sec, automatic | `commitDuration = 2400` (~8 hours) | No — just wait |
| Sepolia testnet | ~12 sec, automatic | Same as mainnet | No — just wait |
| Hardhat local dev | Only on transactions | `commitDuration = 5` (instant) | Yes — auto-mine via `POST /api/dev/mine` |

**Production example** (8 hours commit, 4 hours reveal):
```javascript
advanceToVoting(proposalId, auth, 2400, 1200) // blocks ≈ hours at 12s/block
```

**Local dev** (the app uses small durations and auto-mines blocks to skip past deadlines):
```javascript
advanceToVoting(proposalId, auth, 5, 5)       // 5 blocks, mined instantly
```

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for detailed diagrams.

**On-chain:** Voting integrity, delegation, zkSNARK verification
**Off-chain:** Proposal metadata, fast queries, IPFS coordination
**Event-driven:** Backend syncs on-chain events to PostgreSQL cache

## Project Structure

```
contracts/        Solidity smart contracts
ignition/modules/ Hardhat Ignition deploy modules
migrations/sql/   PostgreSQL migrations
src/api/          Express backend
src/components/   React frontend components
src/contexts/     React context providers
src/hooks/        Custom React hooks
src/utils/        Shared utilities
test/             Tests (contracts, API, E2E)
scripts/          Development scripts
.github/          CI/CD workflows
docs/             Architecture & planning docs
```

## Environment Variables

Copy `.env.example` to `.env` and configure. Key variables:

- `DATABASE_URL` — PostgreSQL connection
- `ETHEREUM_RPC_URL` — Blockchain node (default: Hardhat localhost)
- `REACT_APP_API_URL` — Backend URL for frontend
- Contract addresses (populated after deployment)

## License

MIT
