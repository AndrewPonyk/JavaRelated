const { buildModule } = require("@nomicfoundation/hardhat-ignition/modules");

/**
 * Hardhat Ignition deployment module for the Voting System.
 *
 * Deploy order:
 * 1. DelegationRegistry (standalone)
 * 2. ProposalManager (standalone)
 * 3. VotingSystem (depends on DelegationRegistry address)
 * 4. ZkVerifier (standalone, linked to VotingSystem post-deploy)
 */
module.exports = buildModule("VotingSystem", (m) => {
  // 1. Deploy DelegationRegistry
  const delegationRegistry = m.contract("DelegationRegistry");

  // 2. Deploy ProposalManager
  const proposalManager = m.contract("ProposalManager");

  // 3. Deploy VotingSystem with DelegationRegistry address
  const votingSystem = m.contract("VotingSystem", [delegationRegistry]);

  // 4. Deploy ZkVerifier (not linked by default — run trusted setup first)
  const zkVerifier = m.contract("ZkVerifier");

  return { delegationRegistry, proposalManager, votingSystem, zkVerifier };
});
