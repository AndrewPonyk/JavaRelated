/**
 * Seed the local Hardhat/localhost blockchain with test data.
 * Creates sample proposals and delegates for development testing.
 *
 * Usage: npx hardhat run scripts/seed.js --network localhost
 */

const hre = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  const accounts = await hre.ethers.getSigners();
  const [admin, voter1, voter2, voter3] = accounts;

  console.log("Seeding with accounts:");
  console.log("  Admin:", admin.address);
  console.log("  Voter1:", voter1.address);
  console.log("  Voter2:", voter2.address);
  console.log("  Voter3:", voter3.address);

  // Resolve deployed contract addresses from Ignition deployments
  const ignitionDir = path.join(__dirname, "../ignition/deployments");
  let votingAddr, proposalAddr, delegationAddr;

  if (fs.existsSync(ignitionDir)) {
    const chains = fs.readdirSync(ignitionDir);
    for (const chain of chains) {
      const addrFile = path.join(ignitionDir, chain, "deployed_addresses.json");
      if (fs.existsSync(addrFile)) {
        const addrs = JSON.parse(fs.readFileSync(addrFile, "utf8"));
        for (const [key, addr] of Object.entries(addrs)) {
          if (key.includes("VotingSystem") && !key.includes("Module")) votingAddr = addr;
          if (key.includes("ProposalManager")) proposalAddr = addr;
          if (key.includes("DelegationRegistry")) delegationAddr = addr;
        }
      }
    }
  }

  // Fall back to env vars
  votingAddr = votingAddr || process.env.VOTING_SYSTEM_ADDRESS;
  proposalAddr = proposalAddr || process.env.PROPOSAL_MANAGER_ADDRESS;
  delegationAddr = delegationAddr || process.env.DELEGATION_REGISTRY_ADDRESS;

  if (!votingAddr || !proposalAddr || !delegationAddr) {
    console.error("Contract addresses not found. Deploy contracts first with: npm run deploy:localhost");
    process.exit(1);
  }

  // Attach to deployed contracts
  const voting = await hre.ethers.getContractAt("VotingSystem", votingAddr);
  const proposals = await hre.ethers.getContractAt("ProposalManager", proposalAddr);
  const delegation = await hre.ethers.getContractAt("DelegationRegistry", delegationAddr);

  // Create sample proposals via ProposalManager
  console.log("\n--- Creating proposals ---");
  const THREE_DAYS = 3 * 24 * 60 * 60;

  await proposals.connect(voter1).submitProposal(
    "Increase Treasury Allocation",
    "QmSampleCid1",
    THREE_DAYS + 1
  );
  console.log("Proposal 1 created: Increase Treasury Allocation");

  await proposals.connect(voter2).submitProposal(
    "Approve New Community Guidelines",
    "QmSampleCid2",
    THREE_DAYS + 1
  );
  console.log("Proposal 2 created: Approve New Community Guidelines");

  // Create a voting proposal on VotingSystem
  console.log("\n--- Creating on-chain vote ---");
  const tx = await voting.createProposal("QmVoteCid1", 50, 50, 3);
  const receipt = await tx.wait();
  console.log("On-chain vote created: id=1, 3 options, 50-block commit, 50-block reveal");

  // Set up some delegations
  console.log("\n--- Setting up delegations ---");
  await delegation.connect(voter3).delegateVote(voter1.address, 0);
  console.log(`${voter3.address.slice(0, 10)}... delegated to ${voter1.address.slice(0, 10)}... (global)`);

  // Verify
  const power = await delegation.getVotingPower(voter1.address);
  console.log(`${voter1.address.slice(0, 10)}... voting power: ${power.toString()}`);

  console.log("\n=== Seeding complete! ===");
  console.log("Contract addresses:");
  console.log("  VotingSystem:", votingAddr);
  console.log("  ProposalManager:", proposalAddr);
  console.log("  DelegationRegistry:", delegationAddr);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Seeding failed:", err);
    process.exit(1);
  });
