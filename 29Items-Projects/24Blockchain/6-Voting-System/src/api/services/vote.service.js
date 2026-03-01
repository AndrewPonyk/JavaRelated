const db = require("../utils/db");
const logger = require("../utils/logger");

/**
 * Get cached vote results for a proposal.
 */
async function getResults(proposalId) {
  const proposal = await db.query("SELECT * FROM proposals WHERE id = $1", [proposalId]);
  if (proposal.rows.length === 0) return null;

  const options = await db.query(
    "SELECT option_index, label, vote_count FROM proposal_options WHERE proposal_id = $1 ORDER BY option_index",
    [proposalId]
  );

  const totalVotes = options.rows.reduce((sum, opt) => sum + (opt.vote_count || 0), 0);

  return {
    proposalId: parseInt(proposalId, 10),
    phase: proposal.rows[0].phase,
    totalVotes,
    options: options.rows.map((opt) => ({
      index: opt.option_index,
      label: opt.label,
      votes: opt.vote_count || 0,
      percentage: totalVotes > 0 ? ((opt.vote_count / totalVotes) * 100).toFixed(1) : "0.0",
    })),
  };
}

/**
 * Get the number of committed votes for a proposal.
 */
async function getCommitCount(proposalId) {
  const result = await db.query(
    "SELECT COUNT(*) as count FROM vote_commits WHERE proposal_id = $1",
    [proposalId]
  );
  return parseInt(result.rows[0].count, 10);
}

/**
 * Get the number of revealed votes for a proposal.
 */
async function getRevealCount(proposalId) {
  const result = await db.query(
    "SELECT COUNT(*) as count FROM vote_reveals WHERE proposal_id = $1",
    [proposalId]
  );
  return parseInt(result.rows[0].count, 10);
}

/**
 * Get list of voter addresses that have committed (public info, not choices).
 */
async function getVoterList(proposalId) {
  const result = await db.query(
    "SELECT voter_address, committed_at FROM vote_commits WHERE proposal_id = $1 ORDER BY committed_at",
    [proposalId]
  );
  return result.rows;
}

/**
 * Process a VoteCommitted event from the blockchain.
 */
async function handleVoteCommittedEvent({ proposalId, voter, commitHash, txHash, blockNumber }) {
  try {
    await db.query(
      `INSERT INTO vote_commits (proposal_id, voter_address, commit_hash, tx_hash, block_number)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (proposal_id, voter_address) DO NOTHING`,
      [proposalId, voter.toLowerCase(), commitHash || "", txHash, blockNumber]
    );

    // Update sync state
    await db.query(
      `INSERT INTO sync_state (contract_name, last_block) VALUES ('VotingSystem', $1)
       ON CONFLICT (contract_name) DO UPDATE SET last_block = GREATEST(sync_state.last_block, $1), updated_at = NOW()`,
      [blockNumber]
    );

    logger.info(`Processed VoteCommitted: proposal=${proposalId}, voter=${voter}`);
  } catch (err) {
    logger.error(`Failed to process VoteCommitted event: ${err.message}`);
  }
}

/**
 * Process a VoteRevealed event from the blockchain.
 */
async function handleVoteRevealedEvent({ proposalId, voter, choice, txHash, blockNumber }) {
  try {
    await db.query(
      `INSERT INTO vote_reveals (proposal_id, voter_address, choice, tx_hash, block_number)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (proposal_id, voter_address) DO NOTHING`,
      [proposalId, voter.toLowerCase(), parseInt(choice, 10), txHash, blockNumber]
    );

    // Update cached tally
    await db.query(
      `UPDATE proposal_options SET vote_count = vote_count + 1
       WHERE proposal_id = $1 AND option_index = $2`,
      [proposalId, parseInt(choice, 10)]
    );

    await db.query(
      "UPDATE proposals SET total_votes = total_votes + 1, updated_at = NOW() WHERE chain_proposal_id = $1 OR id = $1",
      [proposalId]
    );

    // Update sync state
    await db.query(
      `INSERT INTO sync_state (contract_name, last_block) VALUES ('VotingSystem', $1)
       ON CONFLICT (contract_name) DO UPDATE SET last_block = GREATEST(sync_state.last_block, $1), updated_at = NOW()`,
      [blockNumber]
    );

    logger.info(`Processed VoteRevealed: proposal=${proposalId}, voter=${voter}, choice=${choice}`);
  } catch (err) {
    logger.error(`Failed to process VoteRevealed event: ${err.message}`);
  }
}

module.exports = {
  getResults,
  getCommitCount,
  getRevealCount,
  getVoterList,
  handleVoteCommittedEvent,
  handleVoteRevealedEvent,
};
