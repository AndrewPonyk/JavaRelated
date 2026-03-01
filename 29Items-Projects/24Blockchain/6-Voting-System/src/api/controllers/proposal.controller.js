const proposalService = require("../services/proposal.service");
const ipfsService = require("../services/ipfs.service");
const { validationResult } = require("express-validator");
const logger = require("../utils/logger");

/**
 * GET /api/proposals
 * List all proposals with optional filtering by phase.
 */
async function listProposals(req, res, next) {
  try {
    const { phase, page = 1, limit = 20 } = req.query;
    const proposals = await proposalService.listProposals({
      phase,
      page: parseInt(page, 10),
      limit: Math.min(parseInt(limit, 10) || 20, 100),
    });
    const total = await proposalService.getProposalCount(phase);

    res.json({
      data: proposals,
      pagination: {
        page: parseInt(page, 10),
        limit: parseInt(limit, 10) || 20,
        total,
      },
    });
  } catch (err) {
    next(err);
  }
}

/**
 * GET /api/proposals/:id
 * Get a single proposal with its options and amendments.
 */
async function getProposal(req, res, next) {
  try {
    const proposal = await proposalService.getProposalById(req.params.id);
    if (!proposal) {
      return res.status(404).json({ error: { code: "NOT_FOUND", message: "Proposal not found" } });
    }
    res.json({ data: proposal });
  } catch (err) {
    next(err);
  }
}

/**
 * POST /api/proposals
 * Create a new proposal (off-chain draft). Authenticated.
 */
async function createProposal(req, res, next) {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ error: { code: "VALIDATION_ERROR", details: errors.array() } });
    }

    const { title, description, options } = req.body;
    const proposerAddress = req.user.walletAddress;

    const proposal = await proposalService.createProposal({
      title,
      description,
      options,
      proposerAddress,
    });

    logger.info(`Proposal created: ${proposal.id} by ${proposerAddress}`);
    res.status(201).json({ data: proposal });
  } catch (err) {
    next(err);
  }
}

/**
 * PUT /api/proposals/:id
 * Update a proposal (only in draft phase, only by the proposer). Authenticated.
 */
async function updateProposal(req, res, next) {
  try {
    const proposal = await proposalService.getProposalById(req.params.id);
    if (!proposal) {
      return res.status(404).json({ error: { code: "NOT_FOUND", message: "Proposal not found" } });
    }
    if (proposal.proposer_address !== req.user.walletAddress) {
      return res.status(403).json({ error: { code: "FORBIDDEN", message: "Not the proposer" } });
    }
    if (proposal.phase !== "draft") {
      return res.status(400).json({ error: { code: "INVALID_STATE", message: "Can only edit drafts" } });
    }

    const updated = await proposalService.updateProposal(req.params.id, req.body);
    res.json({ data: updated });
  } catch (err) {
    next(err);
  }
}

/**
 * DELETE /api/proposals/:id
 * Delete a draft proposal. Authenticated, proposer only.
 */
async function deleteProposal(req, res, next) {
  try {
    const proposal = await proposalService.getProposalById(req.params.id);
    if (!proposal) {
      return res.status(404).json({ error: { code: "NOT_FOUND", message: "Proposal not found" } });
    }
    if (proposal.proposer_address !== req.user.walletAddress) {
      return res.status(403).json({ error: { code: "FORBIDDEN", message: "Not the proposer" } });
    }
    if (proposal.phase !== "draft") {
      return res.status(400).json({ error: { code: "INVALID_STATE", message: "Can only delete drafts" } });
    }

    const deleted = await proposalService.deleteProposal(req.params.id);
    if (deleted) {
      res.status(204).end();
    } else {
      res.status(400).json({ error: { code: "DELETE_FAILED", message: "Could not delete" } });
    }
  } catch (err) {
    next(err);
  }
}

/**
 * POST /api/proposals/:id/amendments
 * Add an amendment to a proposal. Authenticated.
 */
async function addAmendment(req, res, next) {
  try {
    const proposal = await proposalService.getProposalById(req.params.id);
    if (!proposal) {
      return res.status(404).json({ error: { code: "NOT_FOUND", message: "Proposal not found" } });
    }
    if (proposal.phase !== "discussion" && proposal.phase !== "draft") {
      return res.status(400).json({ error: { code: "INVALID_STATE", message: "Cannot amend in this phase" } });
    }

    const { content } = req.body;
    if (!content || content.trim().length < 10) {
      return res.status(400).json({ error: { code: "VALIDATION_ERROR", message: "Amendment content must be at least 10 characters" } });
    }

    const contentCid = await ipfsService.pinJSON({
      proposalId: req.params.id,
      content,
      author: req.user.walletAddress,
      timestamp: new Date().toISOString(),
    });

    const amendment = await proposalService.addAmendment({
      proposalId: req.params.id,
      authorAddress: req.user.walletAddress,
      contentCid,
    });

    res.status(201).json({ data: amendment });
  } catch (err) {
    next(err);
  }
}

module.exports = { listProposals, getProposal, createProposal, updateProposal, deleteProposal, addAmendment };
