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
    try {
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
    } catch (err) {
      logger.warn(`Failed to subscribe to VotingSystem events: ${err.message}`);
    }
  }

  if (proposalArtifact && proposalAddress) {
    try {
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
    } catch (err) {
      logger.warn(`Failed to subscribe to ProposalManager events: ${err.message}`);
    }
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
      id: Number(result.id),
      ipfsCid: result.ipfsCid,
      creator: result.creator,
      commitDeadline: Number(result.commitDeadline),
      revealDeadline: Number(result.revealDeadline),
      optionCount: Number(result.optionCount),
      state: Number(result.state),
      totalVotes: Number(result.totalVotes),
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
    const result = await votingSystemContract.methods.getResult(proposalId, optionIndex).call();
    return Number(result);
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
    return Number(power);
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
  return Number(await web3.eth.getBlockNumber());
}

/**
 * Verify an Ethereum signature (for wallet-based auth).
 */
function verifySignature(message, signature) {
  if (!web3) initialize();
  return web3.eth.accounts.recover(message, signature);
}

/**
 * Verify that a contract has deployed code at the given address.
 * Returns true if code exists, false if address is empty/EOA.
 */
async function hasContractCode(address) {
  if (!web3 || !address) return false;
  try {
    const code = await web3.eth.getCode(address);
    return code && code !== "0x" && code !== "0x0";
  } catch {
    return false;
  }
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

/**
 * Get deployed contract addresses.
 */
function getAddresses() {
  const votingArtifact = loadContractArtifact("VotingSystem");
  const proposalArtifact = loadContractArtifact("ProposalManager");
  const delegationArtifact = loadContractArtifact("DelegationRegistry");
  return {
    votingSystem: resolveAddress(votingArtifact, "VOTING_SYSTEM_ADDRESS") || null,
    proposalManager: resolveAddress(proposalArtifact, "PROPOSAL_MANAGER_ADDRESS") || null,
    delegationRegistry: resolveAddress(delegationArtifact, "DELEGATION_REGISTRY_ADDRESS") || null,
  };
}

/**
 * Mine empty blocks on the Hardhat node (dev only).
 * Uses raw JSON-RPC to call hardhat_mine (avoids Web3.js v4 provider API issues).
 */
async function mineBlocks(count) {
  if (!web3) return 0;
  const rpcUrl = process.env.ETHEREUM_RPC_URL || "http://127.0.0.1:8545";
  try {
    const res = await fetch(rpcUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        jsonrpc: "2.0",
        method: "hardhat_mine",
        params: ["0x" + count.toString(16)],
        id: Date.now(),
      }),
    });
    const json = await res.json();
    if (json.error) throw new Error(json.error.message);
    const newBlock = Number(await web3.eth.getBlockNumber());
    logger.info(`Mined ${count} blocks, now at block #${newBlock}`);
    return newBlock;
  } catch (err) {
    logger.error(`Failed to mine blocks: ${err.message}`);
    throw err;
  }
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
  mineBlocks,
  verifySignature,
  getStatus,
  getAddresses,
  hasContractCode,
};
