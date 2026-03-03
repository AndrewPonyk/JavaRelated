const voteService = require("../services/vote.service");
const blockchainService = require("../services/blockchain.service");
const logger = require("../utils/logger");

/**
 * GET /api/proposals/:id/results
 * Get vote results — reads from chain if DB cache has no votes.
 */
async function getResults(req, res, next) {
  try {
    const results = await voteService.getResults(req.params.id);
    if (!results) {
      return res.status(404).json({ error: { code: "NOT_FOUND", message: "Results not available" } });
    }

    // If DB has no votes, try reading directly from chain (only works for tallied proposals)
    if (results.totalVotes === 0 && results.phase === "tallied") {
      const db = require("../utils/db");
      const proposal = await db.query("SELECT chain_proposal_id, option_count FROM proposals WHERE id = $1", [req.params.id]);
      if (proposal.rows.length > 0 && proposal.rows[0].chain_proposal_id) {
        const chainId = proposal.rows[0].chain_proposal_id;
        const onChain = await blockchainService.getOnChainProposal(chainId);
        if (onChain && Number(onChain.state) === 3) { // 3 = Tallied
          const totalVotes = Number(onChain.totalVotes || 0);
          if (totalVotes > 0) {
            const optionCount = Number(onChain.optionCount || proposal.rows[0].option_count);
            for (let i = 0; i < optionCount; i++) {
              const count = Number(await blockchainService.getOnChainResult(chainId, i) || 0);
              if (results.options[i]) {
                results.options[i].votes = count;
                results.options[i].percentage = totalVotes > 0 ? ((count / totalVotes) * 100).toFixed(1) : "0.0";
              }
            }
            results.totalVotes = totalVotes;
          }
        }
      }
    }

    res.json({ data: results });
  } catch (err) {
    next(err);
  }
}

/**
 * GET /api/proposals/:id/commits
 * Get the count of vote commitments for an active proposal.
 */
async function getCommitCount(req, res, next) {
  try {
    const count = await voteService.getCommitCount(req.params.id);
    res.json({ data: { proposalId: parseInt(req.params.id, 10), commitCount: count } });
  } catch (err) {
    next(err);
  }
}

/**
 * GET /api/proposals/:id/voters
 * Get list of addresses that have committed votes (not their choices).
 */
async function getVoterList(req, res, next) {
  try {
    const voters = await voteService.getVoterList(req.params.id);
    res.json({ data: voters });
  } catch (err) {
    next(err);
  }
}

/**
 * GET /api/proposals/:id/status
 * Get the current voting status including on-chain data.
 */
async function getVotingStatus(req, res, next) {
  try {
    const proposalId = req.params.id;
    const commitCount = await voteService.getCommitCount(proposalId);
    const revealCount = await voteService.getRevealCount(proposalId);

    // Look up chain_proposal_id from database — do NOT use database ID for on-chain queries
    const db = require("../utils/db");
    const proposal = await db.query("SELECT chain_proposal_id FROM proposals WHERE id = $1", [proposalId]);
    const chainProposalId = proposal.rows.length > 0 ? proposal.rows[0].chain_proposal_id : null;

    // Only query on-chain data if we have a valid chain proposal ID
    let onChainData = null;
    if (chainProposalId) {
      onChainData = await blockchainService.getOnChainProposal(chainProposalId);
    }

    const currentBlock = await blockchainService.getBlockNumber();

    res.json({
      data: {
        proposalId: parseInt(proposalId, 10),
        commitCount,
        revealCount,
        currentBlock,
        onChain: onChainData,
      },
    });
  } catch (err) {
    next(err);
  }
}

module.exports = { getResults, getCommitCount, getVoterList, getVotingStatus };
