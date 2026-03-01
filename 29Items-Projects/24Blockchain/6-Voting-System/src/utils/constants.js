// Contract addresses — populated after deployment
export const CONTRACT_ADDRESSES = {
  votingSystem: process.env.REACT_APP_VOTING_SYSTEM_ADDRESS || "",
  proposalManager: process.env.REACT_APP_PROPOSAL_MANAGER_ADDRESS || "",
  delegationRegistry: process.env.REACT_APP_DELEGATION_REGISTRY_ADDRESS || "",
};

// API base URL
export const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:3001/api";

// Supported chain IDs
export const SUPPORTED_CHAINS = {
  1: "Ethereum Mainnet",
  5: "Goerli Testnet",
  11155111: "Sepolia Testnet",
  1337: "Hardhat (Local)",
};

// Proposal phases (matches contract and database enums)
export const PROPOSAL_PHASES = {
  DRAFT: "draft",
  DISCUSSION: "discussion",
  COMMIT: "commit",
  REVEAL: "reveal",
  TALLIED: "tallied",
  CANCELLED: "cancelled",
};
