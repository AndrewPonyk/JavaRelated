const voteService = require("../services/vote.service");
const blockchainService = require("../services/blockchain.service");
const logger = require("../utils/logger");

/**
 * GET /api/proposals/:id/results
 * Get cached vote results for a proposal.
 */
async function getResults(req, res, next) {
  try {
    const results = await voteService.getResults(req.params.id);
    if (!results) {
      return res.status(404).json({ error: { code: "NOT_FOUND", message: "Results not available" } });
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

    // Try to get on-chain data
    const onChainData = await blockchainService.getOnChainProposal(proposalId);

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
