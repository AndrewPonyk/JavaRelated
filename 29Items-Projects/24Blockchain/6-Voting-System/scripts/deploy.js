/**
 * Simple deployment script for Voting System contracts.
 * Usage: npx hardhat run scripts/deploy.js --network localhost
 */

const hre = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  console.log("Deploying Voting System contracts to:", hre.network.name);

  const [deployer] = await hre.ethers.getSigners();
  console.log("Deployer account:", deployer.address);

  // 1. Deploy DelegationRegistry
  console.log("\n1. Deploying DelegationRegistry...");
  const DelegationRegistry = await hre.ethers.getContractFactory("DelegationRegistry");
  const delegationRegistry = await DelegationRegistry.deploy();
  await delegationRegistry.waitForDeployment();
  const delegationAddress = await delegationRegistry.getAddress();
  console.log("   DelegationRegistry deployed to:", delegationAddress);

  // 2. Deploy ProposalManager
  console.log("\n2. Deploying ProposalManager...");
  const ProposalManager = await hre.ethers.getContractFactory("ProposalManager");
  const proposalManager = await ProposalManager.deploy();
  await proposalManager.waitForDeployment();
  const proposalAddress = await proposalManager.getAddress();
  console.log("   ProposalManager deployed to:", proposalAddress);

  // 3. Deploy VotingSystem (depends on DelegationRegistry)
  console.log("\n3. Deploying VotingSystem...");
  const VotingSystem = await hre.ethers.getContractFactory("VotingSystem");
  const votingSystem = await VotingSystem.deploy(delegationAddress);
  await votingSystem.waitForDeployment();
  const votingAddress = await votingSystem.getAddress();
  console.log("   VotingSystem deployed to:", votingAddress);

  // 4. Deploy ZkVerifier
  console.log("\n4. Deploying ZkVerifier...");
  const ZkVerifier = await hre.ethers.getContractFactory("ZkVerifier");
  const zkVerifier = await ZkVerifier.deploy();
  await zkVerifier.waitForDeployment();
  const zkAddress = await zkVerifier.getAddress();
  console.log("   ZkVerifier deployed to:", zkAddress);

  // Save deployment addresses
  const deploymentDir = path.join(__dirname, "../ignition/deployments/chain-1337");
  fs.mkdirSync(deploymentDir, { recursive: true });

  const addresses = {
    "VotingSystemModule#DelegationRegistry": delegationAddress,
    "VotingSystemModule#ProposalManager": proposalAddress,
    "VotingSystemModule#VotingSystem": votingAddress,
    "VotingSystemModule#ZkVerifier": zkAddress,
  };

  fs.writeFileSync(
    path.join(deploymentDir, "deployed_addresses.json"),
    JSON.stringify(addresses, null, 2)
  );

  console.log("\n=== Deployment Complete! ===");
  console.log("\nContract addresses:");
  console.log("  VotingSystem:", votingAddress);
  console.log("  ProposalManager:", proposalAddress);
  console.log("  DelegationRegistry:", delegationAddress);
  console.log("  ZkVerifier:", zkAddress);

  console.log("\nAdd these to your .env file:");
  console.log(`  VOTING_SYSTEM_ADDRESS=${votingAddress}`);
  console.log(`  PROPOSAL_MANAGER_ADDRESS=${proposalAddress}`);
  console.log(`  DELEGATION_REGISTRY_ADDRESS=${delegationAddress}`);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Deployment failed:", err);
    process.exit(1);
  });
