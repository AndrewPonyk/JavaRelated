const db = require("../utils/db");
const ipfsService = require("./ipfs.service");
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
  syncFromChain,
  addAmendment,
  getProposalCount,
};
