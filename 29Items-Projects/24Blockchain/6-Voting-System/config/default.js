/**
 * Default configuration shared across all environments.
 * Environment-specific overrides come from .env files or platform secrets.
 */
module.exports = {
  server: {
    port: parseInt(process.env.PORT, 10) || 3001,
    corsOrigin: process.env.CORS_ORIGIN || "http://localhost:3000",
  },
  database: {
    url: process.env.DATABASE_URL || "postgresql://postgres:postgres@localhost:5432/voting_system",
    poolMax: parseInt(process.env.DB_POOL_MAX, 10) || 20,
  },
  blockchain: {
    rpcUrl: process.env.ETHEREUM_RPC_URL || "http://127.0.0.1:8545",
    chainId: parseInt(process.env.CHAIN_ID, 10) || 1337,
  },
  ipfs: {
    apiUrl: process.env.IPFS_API_URL || "http://127.0.0.1:5001",
    gatewayUrl: process.env.IPFS_GATEWAY_URL || "http://127.0.0.1:8080",
  },
  logging: {
    level: process.env.LOG_LEVEL || "info",
  },
};
