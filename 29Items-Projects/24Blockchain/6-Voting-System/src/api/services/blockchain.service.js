const { Web3 } = require("web3");
const path = require("path");
const fs = require("fs");
const logger = require("../utils/logger");

let web3;
let web3Ws;
let votingSystemContract;
let proposalManagerContract;
let delegationRegistryContract;
let eventSubscriptions = [];

/**
 * Load a compiled contract ABI from the Hardhat artifacts directory.
 * Falls back to the legacy Truffle build path if the Hardhat path doesn't exist.
 * Returns null if neither path exists (contracts not yet compiled).
 */
function loadContractArtifact(contractName) {
  // Hardhat artifact path: artifacts/contracts/<Name>.sol/<Name>.json
  const hardhatPath = path.join(__dirname, `../../../artifacts/contracts/${contractName}.sol/${contractName}.json`);
  if (fs.existsSync(hardhatPath)) {
    return JSON.parse(fs.readFileSync(hardhatPath, "utf8"));
  }

  // Fallback: legacy Truffle path
  const trufflePath = path.join(__dirname, `../../../build/contracts/${contractName}.json`);
  if (fs.existsSync(trufflePath)) {
    return JSON.parse(fs.readFileSync(trufflePath, "utf8"));
  }

  logger.warn(`Contract artifact not found for ${contractName}. Run 'npx hardhat compile' first.`);
  return null;
}

/**
 * Resolve the deployed address from environment variable or Hardhat Ignition deployment.
 */
function resolveAddress(artifact, envVar) {
  if (process.env[envVar]) {
    return process.env[envVar];
  }

  // Try to read from Hardhat Ignition deployed_addresses.json
  const ignitionPath = path.join(__dirname, "../../../ignition/deployments");
  if (fs.existsSync(ignitionPath)) {
    const chains = fs.readdirSync(ignitionPath);
    for (const chain of chains) {
      const addressFile = path.join(ignitionPath, chain, "deployed_addresses.json");
      if (fs.existsSync(addressFile)) {
        const addresses = JSON.parse(fs.readFileSync(addressFile, "utf8"));
        // Ignition keys are like "VotingSystem#ContractName"
        for (const [key, address] of Object.entries(addresses)) {
          const contractName = key.split("#").pop();
          const envContractName = envVar.replace("_ADDRESS", "").replace(/_/g, "");
          if (contractName.toUpperCase() === envContractName.toUpperCase()) {
            return address;
          }
        }
      }
    }
  }

  // Legacy Truffle networks map
  if (artifact && artifact.networks) {
    const networkIds = Object.keys(artifact.networks);
    if (networkIds.length > 0) {
      const latest = networkIds[networkIds.length - 1];
      return artifact.networks[latest].address;
    }
  }

  return null;
}

/**
 * Initialize Web3 connection and contract instances.
 */
function initialize() {
  const rpcUrl = process.env.ETHEREUM_RPC_URL || "http://127.0.0.1:8545";

  // HTTP provider for read calls
  web3 = new Web3(rpcUrl);

  // WebSocket provider for event subscriptions
  const wsUrl = rpcUrl.replace(/^http/, "ws");
  try {
    web3Ws = new Web3(wsUrl);
  } catch (err) {
    logger.warn(`WebSocket connection failed (${wsUrl}), event listening disabled: ${err.message}`);
    web3Ws = null;
  }

  // Load contract artifacts
  const votingArtifact = loadContractArtifact("VotingSystem");
  const proposalArtifact = loadContractArtifact("ProposalManager");
  const delegationArtifact = loadContractArtifact("DelegationRegistry");

  // Initialize VotingSystem contract
  const votingAddress = resolveAddress(votingArtifact, "VOTING_SYSTEM_ADDRESS");
  if (votingArtifact && votingAddress) {
    votingSystemContract = new web3.eth.Contract(votingArtifact.abi, votingAddress);
    logger.info(`VotingSystem contract loaded at ${votingAddress}`);
  } else {
    logger.warn("VotingSystem contract not available. Deploy contracts first.");
  }

  // Initialize ProposalManager contract
  const proposalAddress = resolveAddress(proposalArtifact, "PROPOSAL_MANAGER_ADDRESS");
  if (proposalArtifact && proposalAddress) {
    proposalManagerContract = new web3.eth.Contract(proposalArtifact.abi, proposalAddress);
    logger.info(`ProposalManager contract loaded at ${proposalAddress}`);
  } else {
    logger.warn("ProposalManager contract not available.");
  }

  // Initialize DelegationRegistry contract
  const delegationAddress = resolveAddress(delegationArtifact, "DELEGATION_REGISTRY_ADDRESS");
  if (delegationArtifact && delegationAddress) {
    delegationRegistryContract = new web3.eth.Contract(delegationArtifact.abi, delegationAddress);
    logger.info(`DelegationRegistry contract loaded at ${delegationAddress}`);
  } else {
    logger.warn("DelegationRegistry contract not available.");
  }

  logger.info(`Blockchain service connected to ${rpcUrl}`);
}

/**
 * Subscribe to VotingSystem contract events.
 * Routes events to appropriate callback handlers.
 */
async function startEventListener(handlers = {}) {
  if (!web3Ws) {
    logger.warn("WebSocket not available, event listener not started.");
    return;
  }

  const rpcUrl = process.env.ETHEREUM_RPC_URL || "http://127.0.0.1:8545";
  const wsUrl = rpcUrl.replace(/^http/, "ws");

  // Load artifacts for WS contract instances
  const votingArtifact = loadContractArtifact("VotingSystem");
  const votingAddress = resolveAddress(votingArtifact, "VOTING_SYSTEM_ADDRESS");
  const proposalArtifact = loadContractArtifact("ProposalManager");
  const proposalAddress = resolveAddress(proposalArtifact, "PROPOSAL_MANAGER_ADDRESS");

  if (votingArtifact && votingAddress) {
    const wsVoting = new web3Ws.eth.Contract(votingArtifact.abi, votingAddress);

    const commitSub = wsVoting.events.VoteCommitted()
      .on("data", (event) => {
        logger.info(`Event VoteCommitted: proposal=${event.returnValues.proposalId}, voter=${event.returnValues.voter}`);
        if (handlers.onVoteCommitted) {
          handlers.onVoteCommitted({
            proposalId: event.returnValues.proposalId,
            voter: event.returnValues.voter,
            txHash: event.transactionHash,
            blockNumber: event.blockNumber,
          });
        }
      })
      .on("error", (err) => logger.error("VoteCommitted listener error:", err));

    const revealSub = wsVoting.events.VoteRevealed()
      .on("data", (event) => {
        logger.info(`Event VoteRevealed: proposal=${event.returnValues.proposalId}, voter=${event.returnValues.voter}, choice=${event.returnValues.choice}`);
        if (handlers.onVoteRevealed) {
          handlers.onVoteRevealed({
            proposalId: event.returnValues.proposalId,
            voter: event.returnValues.voter,
            choice: event.returnValues.choice,
            txHash: event.transactionHash,
            blockNumber: event.blockNumber,
          });
        }
      })
      .on("error", (err) => logger.error("VoteRevealed listener error:", err));

    const tallySub = wsVoting.events.ProposalTallied()
      .on("data", (event) => {
        logger.info(`Event ProposalTallied: proposal=${event.returnValues.proposalId}`);
        if (handlers.onProposalTallied) {
          handlers.onProposalTallied({ proposalId: event.returnValues.proposalId });
        }
      })
      .on("error", (err) => logger.error("ProposalTallied listener error:", err));

    eventSubscriptions.push(commitSub, revealSub, tallySub);
  }

  if (proposalArtifact && proposalAddress) {
    const wsProposal = new web3Ws.eth.Contract(proposalArtifact.abi, proposalAddress);

    const proposalSub = wsProposal.events.ProposalSubmitted()
      .on("data", (event) => {
        logger.info(`Event ProposalSubmitted: id=${event.returnValues.id}, proposer=${event.returnValues.proposer}`);
        if (handlers.onProposalSubmitted) {
          handlers.onProposalSubmitted({
            id: event.returnValues.id,
            proposer: event.returnValues.proposer,
            title: event.returnValues.title,
          });
        }
      })
      .on("error", (err) => logger.error("ProposalSubmitted listener error:", err));

    eventSubscriptions.push(proposalSub);
  }

  logger.info("Event listeners started");
}

/**
 * Stop all event subscriptions.
 */
function stopEventListeners() {
  eventSubscriptions.forEach((sub) => {
    if (sub && typeof sub.unsubscribe === "function") {
      sub.unsubscribe();
    }
  });
  eventSubscriptions = [];
  logger.info("Event listeners stopped");
}

/**
 * Read proposal data directly from the VotingSystem contract.
 */
async function getOnChainProposal(proposalId) {
  if (!votingSystemContract) return null;
  try {
    const result = await votingSystemContract.methods.proposals(proposalId).call();
    return {
      id: result.id,
      ipfsCid: result.ipfsCid,
      creator: result.creator,
      commitDeadline: result.commitDeadline,
      revealDeadline: result.revealDeadline,
      optionCount: result.optionCount,
      state: result.state,
      totalVotes: result.totalVotes,
    };
  } catch (err) {
    logger.error(`Failed to read on-chain proposal ${proposalId}: ${err.message}`);
    return null;
  }
}

/**
 * Read vote result for a specific option from the VotingSystem contract.
 */
async function getOnChainResult(proposalId, optionIndex) {
  if (!votingSystemContract) return null;
  try {
    return await votingSystemContract.methods.getResult(proposalId, optionIndex).call();
  } catch (err) {
    logger.error(`Failed to read on-chain result: ${err.message}`);
    return null;
  }
}

/**
 * Get the effective delegate for a voter from the DelegationRegistry.
 */
async function getEffectiveDelegate(voter, proposalId) {
  if (!delegationRegistryContract) return null;
  try {
    return await delegationRegistryContract.methods.getEffectiveDelegate(voter, proposalId).call();
  } catch (err) {
    logger.error(`Failed to read delegation: ${err.message}`);
    return null;
  }
}

/**
 * Get the voting power for an address from the DelegationRegistry.
 */
async function getOnChainVotingPower(address) {
  if (!delegationRegistryContract) return 1;
  try {
    const power = await delegationRegistryContract.methods.getVotingPower(address).call();
    return parseInt(power, 10);
  } catch (err) {
    logger.error(`Failed to read voting power: ${err.message}`);
    return 1;
  }
}

/**
 * Get the latest block number.
 */
async function getBlockNumber() {
  if (!web3) return 0;
  return await web3.eth.getBlockNumber();
}

/**
 * Verify an Ethereum signature (for wallet-based auth).
 */
function verifySignature(message, signature) {
  if (!web3) initialize();
  return web3.eth.accounts.recover(message, signature);
}

/**
 * Check if contracts are loaded and available.
 */
function getStatus() {
  return {
    connected: !!web3,
    votingSystem: !!votingSystemContract,
    proposalManager: !!proposalManagerContract,
    delegationRegistry: !!delegationRegistryContract,
    eventListening: eventSubscriptions.length > 0,
  };
}

module.exports = {
  initialize,
  startEventListener,
  stopEventListeners,
  getOnChainProposal,
  getOnChainResult,
  getEffectiveDelegate,
  getOnChainVotingPower,
  getBlockNumber,
  verifySignature,
  getStatus,
};
