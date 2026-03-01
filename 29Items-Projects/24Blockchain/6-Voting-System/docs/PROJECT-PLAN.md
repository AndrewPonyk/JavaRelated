# Voting System - Project Plan

## 1.1 Project File Structure

```
6-Voting-System/
├── contracts/                    # Solidity smart contracts
│   ├── VotingSystem.sol          # Main voting logic + commit-reveal
│   ├── ProposalManager.sol       # Proposal creation & lifecycle
│   ├── DelegationRegistry.sol    # Vote delegation & revocation
│   ├── zkVerifier.sol            # zkSNARK proof verifier (auto-generated)
│   └── zk/                       # Zero-knowledge circuit definitions
│       ├── vote.circom            # Circom circuit for anonymous voting
│       └── build/                 # Compiled circuit artifacts
├── ignition/
│   └── modules/
│       └── VotingSystem.js       # Hardhat Ignition deploy module
├── migrations/
│   └── sql/
│       ├── 001_create_users.sql          # Off-chain user profiles
│       ├── 002_create_proposals.sql      # Proposal metadata cache
│       └── 003_create_vote_records.sql   # Vote audit trail
├── src/
│   ├── api/                      # Node.js backend (Express)
│   │   ├── server.js             # Express app entry point
│   │   ├── controllers/
│   │   │   ├── proposal.controller.js
│   │   │   ├── vote.controller.js
│   │   │   └── delegation.controller.js
│   │   ├── services/
│   │   │   ├── proposal.service.js
│   │   │   ├── vote.service.js
│   │   │   ├── delegation.service.js
│   │   │   ├── blockchain.service.js
│   │   │   └── ipfs.service.js
│   │   ├── middleware/
│   │   │   ├── auth.middleware.js
│   │   │   ├── validate.middleware.js
│   │   │   └── errorHandler.js
│   │   ├── routes/
│   │   │   └── index.js
│   │   └── utils/
│   │       ├── db.js             # PostgreSQL connection pool
│   │       ├── web3.js           # Web3 provider setup
│   │       └── logger.js
│   ├── components/               # React frontend
│   │   ├── Proposal/
│   │   │   ├── ProposalList.jsx
│   │   │   ├── ProposalDetail.jsx
│   │   │   └── CreateProposal.jsx
│   │   ├── Voting/
│   │   │   ├── VotingBooth.jsx
│   │   │   └── ResultsDisplay.jsx
│   │   ├── Delegation/
│   │   │   └── DelegateVote.jsx
│   │   ├── Layout/
│   │   │   ├── Header.jsx
│   │   │   └── Footer.jsx
│   │   └── common/
│   │       ├── LoadingSpinner.jsx
│   │       └── ErrorBoundary.jsx
│   ├── hooks/
│   │   ├── useContract.js
│   │   └── useWeb3.js
│   ├── contexts/
│   │   └── Web3Context.js
│   ├── utils/
│   │   ├── zkProof.js            # zkSNARK proof generation (client-side)
│   │   └── constants.js
│   ├── pages/
│   │   ├── Home.jsx
│   │   ├── ProposalPage.jsx
│   │   └── DelegationPage.jsx
│   ├── App.jsx
│   └── index.js
├── public/
│   └── index.html
├── test/
│   ├── contracts/
│   │   ├── VotingSystem.test.js
│   │   ├── ProposalManager.test.js
│   │   └── DelegationRegistry.test.js
│   ├── api/
│   │   ├── proposal.test.js
│   │   └── vote.test.js
│   └── e2e/
│       └── voting-flow.test.js
├── scripts/
│   ├── generate-zk-keys.sh       # Trusted setup for zkSNARKs
│   ├── seed.js                    # Seed local blockchain with test data
│   └── mythx-analyze.sh           # Run MythX security audit
├── config/
│   └── default.js
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── deploy.yml
├── .env.example
├── .eslintrc.json
├── .prettierrc
├── .solhint.json
├── hardhat.config.js
├── package.json
├── Dockerfile
├── docker-compose.yml
└── docs/
    ├── PROJECT-PLAN.md
    ├── ARCHITECTURE.md
    └── TECH-NOTES.md
```

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)

- [ ] Initialize Node.js project with `package.json` and dependency installation
- [ ] Configure Hardhat project (`hardhat.config.js`) with local network
- [ ] Set up PostgreSQL schema and migration scripts
- [ ] Write `VotingSystem.sol` — commit-reveal scheme with vote hashing
- [ ] Write `ProposalManager.sol` — proposal creation, amendment, state machine
- [ ] Write `DelegationRegistry.sol` — delegation with revocation
- [ ] Deploy contracts to local Hardhat node via Ignition deploy modules
- [ ] Set up Express backend with health-check endpoint
- [ ] Set up React frontend with Web3 provider and Metamask connection
- [ ] Implement `blockchain.service.js` — Web3 contract interaction layer
- [ ] Write unit tests for all three smart contracts
- [ ] Configure ESLint, Prettier, Solhint for code quality

### Phase 2: Core Features (Medium Priority)

- [ ] Implement zkSNARK circuit (`vote.circom`) for anonymous voting proofs
- [ ] Generate proving/verification keys (trusted setup script)
- [ ] Deploy `zkVerifier.sol` (auto-generated from circom)
- [ ] Integrate zkSNARK proof generation in frontend (`zkProof.js`)
- [ ] Build proposal CRUD API endpoints with input validation
- [ ] Build voting API — commit phase, reveal phase, result tallying
- [ ] Build delegation API — delegate, revoke, query effective voting power
- [ ] Implement IPFS integration for proposal document storage
- [ ] Build frontend pages: proposal list, voting booth, delegation manager
- [ ] Add Metamask wallet connection flow and transaction signing UX
- [ ] Implement API authentication middleware (signature-based with Metamask)
- [ ] Run MythX automated audit on all contracts — fix findings
- [ ] Write integration tests for API endpoints
- [ ] Set up GitHub Actions CI pipeline (lint, test, build)

### Phase 3: Polish & Optimization (Lower Priority)

- [ ] Add real-time vote status updates via WebSocket
- [ ] Implement gas optimization in smart contracts
- [ ] Add proposal discussion/comment system (IPFS-backed)
- [ ] Build admin dashboard for managing voting periods
- [ ] Implement quadratic voting support as alternative voting mode
- [ ] Write end-to-end tests covering full voting lifecycle
- [ ] Set up GitHub Actions CD pipeline for testnet deployment
- [ ] Configure IPFS pinning service for production frontend hosting
- [ ] Add monitoring and alerting (contract events, API health)
- [ ] Write user documentation and API reference
- [ ] Conduct manual security review and penetration testing
- [ ] Performance testing under load (concurrent voters)
