const db = require("../utils/db");
const ipfsService = require("./ipfs.service");
const blockchainService = require("./blockchain.service");
const logger = require("../utils/logger");

/**
 * List proposals with optional phase filter and pagination.
 */
async function listProposals({ phase, page = 1, limit = 20 }) {
  const offset = (page - 1) * limit;
  let query = "SELECT * FROM proposals";
  const params = [];

  if (phase) {
    query += " WHERE phase = $1";
    params.push(phase);
  }

  query += ` ORDER BY created_at DESC LIMIT $${params.length + 1} OFFSET $${params.length + 2}`;
  params.push(limit, offset);

  const result = await db.query(query, params);
  return result.rows;
}

/**
 * Get a single proposal by ID, including its options and amendments.
 */
async function getProposalById(id) {
  const proposalResult = await db.query("SELECT * FROM proposals WHERE id = $1", [id]);
  if (proposalResult.rows.length === 0) return null;

  const proposal = proposalResult.rows[0];

  const optionsResult = await db.query(
    "SELECT * FROM proposal_options WHERE proposal_id = $1 ORDER BY option_index",
    [id]
  );
  proposal.options = optionsResult.rows;

  const amendmentsResult = await db.query(
    "SELECT * FROM amendments WHERE proposal_id = $1 ORDER BY created_at",
    [id]
  );
  proposal.amendments = amendmentsResult.rows;

  return proposal;
}

/**
 * Create a new proposal in draft phase.
 */
async function createProposal({ title, description, options, proposerAddress }) {
  // Upload full description to IPFS
  const descriptionCid = await ipfsService.pinJSON({ title, description, options });

  const result = await db.query(
    `INSERT INTO proposals (title, description_cid, proposer_address, phase, option_count)
     VALUES ($1, $2, $3, 'draft', $4) RETURNING *`,
    [title, descriptionCid, proposerAddress, options.length]
  );

  const proposal = result.rows[0];

  // Insert option labels
  for (let i = 0; i < options.length; i++) {
    await db.query(
      "INSERT INTO proposal_options (proposal_id, option_index, label) VALUES ($1, $2, $3)",
      [proposal.id, i, options[i]]
    );
  }

  logger.info(`Proposal ${proposal.id} created by ${proposerAddress}`);
  return await getProposalById(proposal.id);
}

/**
 * Update a draft proposal.
 */
async function updateProposal(id, { title, description, options }) {
  const existing = await getProposalById(id);
  if (!existing) return null;

  // Re-upload to IPFS if description or title changed
  let newCid = existing.description_cid;
  if (description || title) {
    newCid = await ipfsService.pinJSON({
      title: title || existing.title,
      description: description || "",
      options: options || existing.options.map((o) => o.label),
    });
  }

  const result = await db.query(
    `UPDATE proposals SET
       title = COALESCE($1, title),
       description_cid = $2,
       updated_at = NOW()
     WHERE id = $3 RETURNING *`,
    [title, newCid, id]
  );

  // Update options if provided
  if (options && options.length >= 2) {
    await db.query("DELETE FROM proposal_options WHERE proposal_id = $1", [id]);
    for (let i = 0; i < options.length; i++) {
      await db.query(
        "INSERT INTO proposal_options (proposal_id, option_index, label) VALUES ($1, $2, $3)",
        [id, i, options[i]]
      );
    }
    await db.query("UPDATE proposals SET option_count = $1 WHERE id = $2", [options.length, id]);
  }

  return await getProposalById(id);
}

/**
 * Delete a proposal (only drafts).
 */
async function deleteProposal(id) {
  const result = await db.query(
    "DELETE FROM proposals WHERE id = $1 AND phase = 'draft' RETURNING id",
    [id]
  );
  return result.rows.length > 0;
}

/**
 * Update a proposal's phase.
 */
async function updatePhase(id, phase) {
  const result = await db.query(
    "UPDATE proposals SET phase = $1, updated_at = NOW() WHERE id = $2 RETURNING *",
    [phase, id]
  );
  return result.rows[0] || null;
}

/**
 * Advance a draft proposal to active voting on-chain.
 * Creates proposal on VotingSystem contract and links it to database record.
 */
async function advanceToVoting(id, { commitDuration = 5, revealDuration = 5 } = {}) {
  const proposal = await getProposalById(id);
  if (!proposal) {
    throw new Error("Proposal not found");
  }
  if (proposal.phase !== "draft") {
    throw new Error("Only draft proposals can be advanced to voting");
  }

  try {
    // Get the VotingSystem contract
    const web3 = blockchainService.getStatus().connected
      ? new (require("web3").Web3)(process.env.ETHEREUM_RPC_URL || "http://127.0.0.1:8545")
      : null;

    if (!web3) {
      throw new Error("Blockchain connection not available");
    }

    // Load contract artifacts and address
    const fs = require("fs");
    const path = require("path");
    const artifactPath = path.join(__dirname, "../../../artifacts/contracts/VotingSystem.sol/VotingSystem.json");
    if (!fs.existsSync(artifactPath)) {
      throw new Error("VotingSystem contract not compiled. Run 'npm run compile' first.");
    }

    const artifact = JSON.parse(fs.readFileSync(artifactPath, "utf8"));

    // Resolve address from ignition/deployments folder
    const ignitionPath = path.join(__dirname, "../../../ignition/deployments");
    let contractAddress = null;
    if (fs.existsSync(ignitionPath)) {
      const chains = fs.readdirSync(ignitionPath);
      for (const chain of chains) {
        const addrFile = path.join(ignitionPath, chain, "deployed_addresses.json");
        if (fs.existsSync(addrFile)) {
          const addresses = JSON.parse(fs.readFileSync(addrFile, "utf8"));
          for (const [key, addr] of Object.entries(addresses)) {
            if (key.endsWith("#VotingSystem")) {
              contractAddress = addr;
              break;
            }
          }
        }
      }
    }

    if (!contractAddress) {
      throw new Error("VOTING_SYSTEM_ADDRESS not found in deployments. Run 'npm run deploy:localhost' first.");
    }

    // Verify the contract actually has code at this address
    const code = await web3.eth.getCode(contractAddress);
    if (!code || code === "0x" || code === "0x0") {
      throw new Error(
        `No contract code at VotingSystem address ${contractAddress}. ` +
        "The Hardhat node may have restarted. Redeploy with: npm run deploy:localhost"
      );
    }

    const contract = new web3.eth.Contract(artifact.abi, contractAddress);

    // Get current block number for deadline calculation
    const currentBlock = Number(await web3.eth.getBlockNumber());
    const commitDeadline = currentBlock + commitDuration;
    const revealDeadline = commitDeadline + revealDuration;

    // Call createProposal on the smart contract
    // Note: This requires a transaction, so we need the caller's address to sign
    // For now, we'll return the data needed for the frontend to send the transaction
    logger.info(`Preparing to advance proposal ${id} to voting phase`);

    return {
      contractAddress,
      commitDeadline: Number(commitDeadline),
      revealDeadline: Number(revealDeadline),
      optionCount: Number(proposal.option_count),
      ipfsCid: proposal.description_cid,
      abi: artifact.abi,
      methodName: "createProposal",
      params: [proposal.description_cid, Number(commitDuration), Number(revealDuration), Number(proposal.option_count)]
    };
  } catch (err) {
    logger.error(`Failed to advance proposal ${id}: ${err.message}`);
    throw err;
  }
}

/**
 * Complete the advancement after on-chain transaction is confirmed.
 */
async function completeAdvancement(id, { chainProposalId, commitDeadline, revealDeadline }) {
  const result = await db.query(
    `UPDATE proposals SET
       chain_proposal_id = $1,
       phase = 'commit',
       commit_deadline = $2,
       reveal_deadline = $3,
       updated_at = NOW()
     WHERE id = $4 RETURNING *`,
    [chainProposalId, commitDeadline, revealDeadline, id]
  );

  if (result.rows.length === 0) {
    throw new Error("Proposal not found");
  }

  logger.info(`Proposal ${id} advanced to voting phase, chain ID: ${chainProposalId}`);
  return await getProposalById(id);
}

/**
 * Sync a proposal from on-chain data.
 */
async function syncFromChain({ chainProposalId, contractAddress, commitDeadline, revealDeadline, phase, title, ipfsCid, proposerAddress, optionCount }) {
  const existing = await db.query(
    "SELECT id FROM proposals WHERE chain_proposal_id = $1",
    [chainProposalId]
  );

  if (existing.rows.length > 0) {
    // Update existing
    await db.query(
      `UPDATE proposals SET
         phase = COALESCE($1, phase),
         commit_deadline = COALESCE($2, commit_deadline),
         reveal_deadline = COALESCE($3, reveal_deadline),
         synced_at = NOW(),
         updated_at = NOW()
       WHERE chain_proposal_id = $4`,
      [phase, commitDeadline, revealDeadline, chainProposalId]
    );
    return existing.rows[0].id;
  }

  // Insert new
  const result = await db.query(
    `INSERT INTO proposals (chain_proposal_id, contract_address, title, description_cid,
       proposer_address, phase, option_count, commit_deadline, reveal_deadline, synced_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW()) RETURNING id`,
    [chainProposalId, contractAddress, title || `Proposal #${chainProposalId}`,
     ipfsCid, proposerAddress, phase || "commit", optionCount, commitDeadline, revealDeadline]
  );
  return result.rows[0].id;
}

/**
 * Add an amendment record.
 */
async function addAmendment({ proposalId, authorAddress, contentCid }) {
  const result = await db.query(
    `INSERT INTO amendments (proposal_id, author_address, content_cid)
     VALUES ($1, $2, $3) RETURNING *`,
    [proposalId, authorAddress, contentCid]
  );
  return result.rows[0];
}

/**
 * Get total count of proposals (for pagination).
 */
async function getProposalCount(phase) {
  let query = "SELECT COUNT(*) as count FROM proposals";
  const params = [];
  if (phase) {
    query += " WHERE phase = $1";
    params.push(phase);
  }
  const result = await db.query(query, params);
  return parseInt(result.rows[0].count, 10);
}

module.exports = {
  listProposals,
  getProposalById,
  createProposal,
  updateProposal,
  deleteProposal,
  updatePhase,
  advanceToVoting,
  completeAdvancement,
  syncFromChain,
  addAmendment,
  getProposalCount,
};
