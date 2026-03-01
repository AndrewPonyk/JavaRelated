const { Web3 } = require("web3");
const logger = require("./logger");

let web3Instance = null;

/**
 * Get or create a Web3 instance.
 * Uses the ETHEREUM_RPC_URL from environment, defaulting to local Hardhat node.
 */
function getWeb3() {
  if (!web3Instance) {
    const rpcUrl = process.env.ETHEREUM_RPC_URL || "http://127.0.0.1:8545";
    web3Instance = new Web3(rpcUrl);
    logger.info(`Web3 connected to ${rpcUrl}`);
  }
  return web3Instance;
}

/**
 * Get a WebSocket-enabled Web3 instance (for event subscriptions).
 */
function getWeb3Ws() {
  const rpcUrl = process.env.ETHEREUM_RPC_URL || "http://127.0.0.1:8545";
  const wsUrl = rpcUrl.replace("http", "ws");
  return new Web3(wsUrl);
}

module.exports = { getWeb3, getWeb3Ws };
