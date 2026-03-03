const express = require("express");
const proposalController = require("../controllers/proposal.controller");
const voteController = require("../controllers/vote.controller");
const delegationController = require("../controllers/delegation.controller");
const authController = require("../controllers/auth.controller");
const { authenticate, optionalAuth } = require("../middleware/auth.middleware");
const { createProposalRules, paginationRules } = require("../middleware/validate.middleware");

const blockchainService = require("../services/blockchain.service");
const fs = require("fs");
const path = require("path");

const router = express.Router();

// --- Contract Addresses + ABIs ---
router.get("/contracts", async (_req, res) => {
  const addresses = blockchainService.getAddresses();
  let votingAbi = null;
  const artifactPath = path.join(__dirname, "../../../artifacts/contracts/VotingSystem.sol/VotingSystem.json");
  try {
    votingAbi = JSON.parse(fs.readFileSync(artifactPath, "utf8")).abi;
  } catch {}

  // Verify contracts have code on-chain
  const hasCode = addresses.votingSystem
    ? await blockchainService.hasContractCode(addresses.votingSystem)
    : false;

  res.json({ data: { ...addresses, votingAbi, contractsDeployed: hasCode } });
});

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
router.post("/proposals/:id/advance", authenticate, proposalController.advanceToVoting);
router.post("/proposals/:id/advance/complete", authenticate, proposalController.completeAdvancement);
router.post("/proposals/:id/phase", proposalController.updatePhase);

// --- Vote Routes ---
router.get("/proposals/:id/results", voteController.getResults);
router.get("/proposals/:id/commits", voteController.getCommitCount);
router.get("/proposals/:id/voters", voteController.getVoterList);
router.get("/proposals/:id/status", voteController.getVotingStatus);

// --- Dev: Mine blocks (Hardhat only) ---
router.post("/dev/mine", async (req, res) => {
  try {
    const count = Math.min(parseInt(req.body.count, 10) || 1, 1000);
    const newBlock = await blockchainService.mineBlocks(count);
    res.json({ data: { mined: count, currentBlock: newBlock } });
  } catch (err) {
    res.status(500).json({ error: { message: err.message } });
  }
});

// --- Delegation Routes ---
router.get("/delegation/:address", delegationController.getDelegationInfo);
router.get("/delegation/:address/power", delegationController.getVotingPower);
router.get("/delegation/:address/history", delegationController.getDelegationHistory);

module.exports = router;
