# Voting System - Technical Notes

## 3.1 CI/CD Pipeline Design

### GitHub Actions CI (`.github/workflows/ci.yml`)

```
Push/PR → Lint → Test → Build → Security Audit
```

**Stages:**

1. **Lint** (parallel)
   - `eslint` on JS/JSX files
   - `solhint` on Solidity contracts
   - `prettier --check` on all files

2. **Test** (parallel, after lint)
   - `hardhat test` — smart contract unit tests on Hardhat Network
   - `jest` — API unit and integration tests
   - `jest` — React component tests

3. **Build** (after tests)
   - `hardhat compile` — compile contracts, generate ABIs
   - `react-scripts build` — production frontend bundle
   - `docker build` — API container image

4. **Security Audit** (after build)
   - `mythx analyze` — automated smart contract vulnerability scan
   - `npm audit` — dependency vulnerability check

### GitHub Actions CD (`.github/workflows/deploy.yml`)

```
Main Branch Merge → Build → Deploy Contracts (Testnet) → Deploy API → Deploy Frontend (IPFS)
```

- Contracts deployed to Sepolia testnet via Hardhat Ignition with Infura.
- API deployed as Docker container (cloud provider of choice).
- Frontend built and pinned to IPFS via Pinata or Infura IPFS.

## 3.2 Testing Strategy

### Smart Contracts (Hardhat + Chai)
- **Unit tests:** Test each contract function in isolation using Hardhat Network.
- **Coverage target:** 90%+ line coverage for all contracts.
- **Key scenarios:**
  - Commit-reveal lifecycle (happy path + edge cases)
  - Delegation chain resolution
  - zkSNARK proof verification (valid + invalid proofs)
  - Access control (only authorized roles can call admin functions)
  - Time-dependent behavior (voting period open/closed)
- **Tool:** `solidity-coverage` for coverage reports.

### API (Jest + Supertest)
- **Unit tests:** Service layer functions mocked from database and blockchain.
- **Integration tests:** Full HTTP request/response testing with `supertest`.
- **Coverage target:** 80%+ line coverage.
- **Key scenarios:**
  - Proposal CRUD operations
  - Signature verification for wallet-based auth
  - Input validation edge cases
  - Error response format consistency

### Frontend (Jest + React Testing Library)
- **Component tests:** Render components with mock data, verify DOM output.
- **Hook tests:** Test `useContract` and `useWeb3` hooks with mocked Web3 provider.
- **Coverage target:** 70%+ line coverage.

### End-to-End (Cypress or Playwright)
- **Full flow tests:** Connect wallet → Create proposal → Vote (commit + reveal) → View results.
- **Run against:** Local Hardhat node + local API + local frontend.
- **CI:** Run in headless mode on GitHub Actions.

## 3.3 Deployment Strategy

### Smart Contracts
1. **Local:** Hardhat node (auto-deployed via `hardhat ignition deploy`).
2. **Testnet:** Sepolia via Infura RPC. Deployer key stored in GitHub Secrets.
3. **Mainnet:** Same Hardhat Ignition module with mainnet Infura endpoint. Requires multi-sig approval before deployment.

### Backend API
1. **Containerized** via Docker (`Dockerfile` + `docker-compose.yml`).
2. **Development:** `docker-compose up` starts API + PostgreSQL + Hardhat node.
3. **Production:** Deploy Docker image to cloud (AWS ECS, GCP Cloud Run, or similar). PostgreSQL as managed service (RDS/Cloud SQL).

### Frontend
1. **Development:** `react-scripts start` with local API proxy.
2. **Production:** Build static bundle → Pin to IPFS via Pinata API → Update ENS domain to point to new IPFS CID.
3. **Fallback:** Also deploy to traditional CDN (Vercel/Netlify) as backup access point.

## 3.4 Environment Management

### Environment Variables
Three environments: `development`, `staging`, `production`.

Configuration loaded from `.env` files (local) or platform secrets (CI/CD).

**Key variables** (see `.env.example`):
- `NODE_ENV` — environment identifier
- `DATABASE_URL` — PostgreSQL connection string
- `ETHEREUM_RPC_URL` — blockchain node endpoint (Hardhat/Infura)
- `DEPLOYER_PRIVATE_KEY` — contract deployment key (NEVER commit)
- `IPFS_API_URL` — IPFS node or Pinata API endpoint
- `IPFS_API_KEY` — Pinata or Infura IPFS credentials
- `MYTHX_API_KEY` — MythX security scanner API key
- `REACT_APP_API_URL` — backend API base URL for frontend
- `REACT_APP_CONTRACT_ADDRESS` — deployed VotingSystem contract address

### Config Hierarchy
1. `.env` file (local overrides, gitignored)
2. `config/default.js` (shared defaults)
3. Environment-specific overrides in CI/CD platform secrets

## 3.5 Version Control Workflow

### Recommended: GitHub Flow (with Protection)

**Rationale:** Simple branching model suits a small-to-medium team. Smart contract changes need careful review, so PR-based workflow with required approvals is essential.

**Branch Strategy:**
- `main` — always deployable, protected branch
- `feature/<name>` — feature branches from main
- `fix/<name>` — bug fix branches
- `audit/<name>` — security audit response branches

**Rules:**
- All changes via pull request — no direct pushes to `main`.
- Require at least 1 approval for API/frontend changes.
- Require at least 2 approvals for smart contract changes (higher risk).
- CI must pass (lint + test + MythX) before merge.
- Squash merge to keep history clean.

**Contract Versioning:**
- Smart contracts are immutable once deployed. Use a `contractVersion` state variable.
- Major contract changes require new deployment + migration script.
- Keep a `DEPLOYMENTS.md` log tracking contract addresses per network.

## 3.6 Common Pitfalls

### Solidity / Smart Contracts
- **Reentrancy attacks:** Use OpenZeppelin's `ReentrancyGuard` on all external calls. MythX will flag these but defense-in-depth matters.
- **Integer overflow:** Solidity 0.8+ has built-in overflow checks, but be aware of `unchecked` blocks.
- **Gas limits:** Avoid unbounded loops (e.g., iterating over all voters). Use pagination patterns.
- **Timestamp manipulation:** Don't rely on `block.timestamp` for precise timing. Miners can manipulate by ~15 seconds. Use block numbers for critical deadlines.
- **Front-running:** The commit-reveal scheme mitigates this, but ensure the commitment hash includes a strong secret (not just the vote choice).

### zkSNARKs
- **Trusted setup:** The ceremony for generating proving/verification keys must be done carefully. Use a multi-party computation (MPC) ceremony for production.
- **Proving key size:** Can be 15-50MB+. Must be downloaded by the browser. Implement caching and progress indicators.
- **Proof generation time:** Client-side proving can take 5-30 seconds depending on circuit complexity and device. Show clear loading states.
- **Circuit complexity:** Keep the circuit minimal. Each additional constraint increases proving time and key size.

### Web3 / Metamask
- **Transaction UX:** Users must approve each transaction in Metamask. The commit-reveal scheme requires TWO transactions — clearly communicate this.
- **Network switching:** Ensure the frontend detects and prompts users to switch to the correct network.
- **Nonce management:** If users send rapid transactions, nonce conflicts can occur. Queue transactions sequentially.
- **Gas estimation:** Always estimate gas before sending. Provide clear error messages when estimation fails (usually means the transaction would revert).

### PostgreSQL / Off-Chain Sync
- **Event replay:** If the backend misses events (downtime), it must be able to replay from a known block number. Store the last processed block in the database.
- **Consistency:** The off-chain cache may lag behind on-chain state. Always show "last synced block" in the UI. For critical reads, query the contract directly.
- **Duplicate events:** Handle idempotently — use unique constraints on (proposalId, voter) pairs.

### IPFS
- **Content persistence:** IPFS does not guarantee persistence. Pin content via Pinata or Infura, and keep CID references in PostgreSQL.
- **Gateway reliability:** Public IPFS gateways can be slow. Use a dedicated gateway or fallback to traditional hosting.
- **Large files:** Keep proposal documents small. For attachments, consider size limits and compression.

### React / Frontend
- **Wallet state management:** Metamask can disconnect, switch accounts, or switch networks at any time. Handle all these events in `Web3Context`.
- **BigNumber handling:** Ethereum values are 256-bit integers. Use `ethers.BigNumber` or `bn.js` — never native JS numbers for wei values.
- **ABI changes:** When contracts are redeployed, the frontend must use matching ABIs. Automate ABI copying from `artifacts/contracts/` to `src/utils/` in the build step.
