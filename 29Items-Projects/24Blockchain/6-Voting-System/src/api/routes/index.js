const express = require("express");
const proposalController = require("../controllers/proposal.controller");
const voteController = require("../controllers/vote.controller");
const delegationController = require("../controllers/delegation.controller");
const authController = require("../controllers/auth.controller");
const { authenticate, optionalAuth } = require("../middleware/auth.middleware");
const { createProposalRules, paginationRules } = require("../middleware/validate.middleware");

const router = express.Router();

// --- Auth Routes ---
router.post("/auth/nonce", authController.requestNonce);
router.get("/auth/me", authenticate, authController.getProfile);
router.put("/auth/me", authenticate, authController.updateProfile);

// --- Proposal Routes ---
router.get("/proposals", paginationRules, proposalController.listProposals);
router.get("/proposals/:id", proposalController.getProposal);
router.post("/proposals", authenticate, createProposalRules, proposalController.createProposal);
router.put("/proposals/:id", authenticate, proposalController.updateProposal);
router.delete("/proposals/:id", authenticate, proposalController.deleteProposal);
router.post("/proposals/:id/amendments", authenticate, proposalController.addAmendment);

// --- Vote Routes ---
router.get("/proposals/:id/results", voteController.getResults);
router.get("/proposals/:id/commits", voteController.getCommitCount);
router.get("/proposals/:id/voters", voteController.getVoterList);
router.get("/proposals/:id/status", voteController.getVotingStatus);

// --- Delegation Routes ---
router.get("/delegation/:address", delegationController.getDelegationInfo);
router.get("/delegation/:address/power", delegationController.getVotingPower);
router.get("/delegation/:address/history", delegationController.getDelegationHistory);

module.exports = router;
