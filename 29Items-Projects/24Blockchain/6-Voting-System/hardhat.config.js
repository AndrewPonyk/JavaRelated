require("dotenv").config();
require("@nomicfoundation/hardhat-toolbox");

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.19",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
      evmVersion: "paris",
    },
  },

  networks: {
    // Local Hardhat node (default)
    hardhat: {
      chainId: 1337,
    },

    // Explicit localhost (for `npx hardhat node`)
    localhost: {
      url: "http://127.0.0.1:8545",
      chainId: 1337,
    },

    // Sepolia Testnet
    // sepolia: {
    //   url: `https://sepolia.infura.io/v3/${process.env.INFURA_PROJECT_ID}`,
    //   accounts: process.env.DEPLOYER_PRIVATE_KEY
    //     ? [process.env.DEPLOYER_PRIVATE_KEY]
    //     : [],
    //   chainId: 11155111,
    // },

    // Mainnet (use with caution)
    // mainnet: {
    //   url: `https://mainnet.infura.io/v3/${process.env.INFURA_PROJECT_ID}`,
    //   accounts: process.env.DEPLOYER_PRIVATE_KEY
    //     ? [process.env.DEPLOYER_PRIVATE_KEY]
    //     : [],
    //   chainId: 1,
    //   gasPrice: 20000000000, // 20 gwei — adjust based on network conditions
    // },
  },

  paths: {
    sources: "./contracts",
    tests: "./test/contracts",
    cache: "./cache",
    artifacts: "./artifacts",
  },
};
