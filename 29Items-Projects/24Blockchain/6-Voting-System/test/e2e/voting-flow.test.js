/**
 * End-to-end test: Full voting lifecycle using Hardhat.
 *
 * This test covers the complete on-chain flow:
 * 1. Deploy contracts
 * 2. Create a proposal
 * 3. Commit votes from multiple voters
 * 4. Advance past commit deadline
 * 5. Reveal votes
 * 6. Advance past reveal deadline
 * 7. Tally results
 * 8. Verify results
 *
 * Also tests delegation integration.
 */

const { expect } = require("chai");
const { ethers } = require("hardhat");
const { mine, time } = require("@nomicfoundation/hardhat-network-helpers");

describe("E2E: Full Voting Lifecycle", function () {
  let voting, proposals, delegation;
  let admin, voter1, voter2, voter3, delegate;
  let secrets;

  before(async function () {
    [admin, voter1, voter2, voter3, delegate] = await ethers.getSigners();

    const DelegationRegistry = await ethers.getContractFactory("DelegationRegistry");
    delegation = await DelegationRegistry.deploy();

    const ProposalManager = await ethers.getContractFactory("ProposalManager");
    proposals = await ProposalManager.deploy();

    const VotingSystem = await ethers.getContractFactory("VotingSystem");
    voting = await VotingSystem.deploy(await delegation.getAddress());

    secrets = {};
  });

  it("Step 1: Create a proposal through ProposalManager", async function () {
    const THREE_DAYS = 3 * 24 * 60 * 60;
    await expect(
      proposals.connect(voter1).submitProposal("Budget Allocation Q1", "QmBudgetCid", THREE_DAYS + 1)
    )
      .to.emit(proposals, "ProposalSubmitted")
      .withArgs(1, voter1.address, "Budget Allocation Q1");
  });

  it("Step 2: Add amendments during discussion", async function () {
    await expect(proposals.connect(voter2).addAmendment(1, "QmAmendment1"))
      .to.emit(proposals, "AmendmentAdded");

    expect(await proposals.getAmendmentCount(1)).to.equal(1);
  });

  it("Step 3: Advance to voting after discussion period", async function () {
    await time.increase(3 * 24 * 60 * 60 + 2);
    await expect(proposals.connect(voter1).advanceToVoting(1))
      .to.emit(proposals, "ProposalAdvanced");
  });

  it("Step 4: Create on-chain voting proposal", async function () {
    // Create voting proposal with 3 options: Yes, No, Abstain
    // Commit: 10 blocks, Reveal: 10 blocks
    await expect(voting.createProposal("QmBudgetCid", 10, 10, 3))
      .to.emit(voting, "ProposalCreated")
      .withArgs(1, admin.address, "QmBudgetCid");
  });

  it("Step 5: Set up delegation (voter3 delegates to delegate)", async function () {
    await delegation.connect(voter3).delegateVote(delegate.address, 0);
    expect(await delegation.getEffectiveDelegate(voter3.address, 1)).to.equal(delegate.address);
  });

  it("Step 6: Commit votes", async function () {
    secrets.secret1 = ethers.hexlify(ethers.randomBytes(32));
    secrets.secret2 = ethers.hexlify(ethers.randomBytes(32));
    secrets.secretDelegate = ethers.hexlify(ethers.randomBytes(32));

    const hash1 = await voting.getCommitHash(1, 0, secrets.secret1); // voter1 votes Yes
    const hash2 = await voting.getCommitHash(1, 1, secrets.secret2); // voter2 votes No
    const hashDelegate = await voting.getCommitHash(1, 0, secrets.secretDelegate); // delegate votes Yes

    await voting.connect(voter1).commitVote(1, hash1);
    await voting.connect(voter2).commitVote(1, hash2);
    await voting.connect(delegate).commitVote(1, hashDelegate); // delegate can vote (incl. voter3's power)
  });

  it("Step 7: Advance past commit deadline and reveal votes", async function () {
    await mine(11);

    await expect(voting.connect(voter1).revealVoteSimple(1, 0, secrets.secret1))
      .to.emit(voting, "VoteRevealed")
      .withArgs(1, voter1.address, 0);

    await expect(voting.connect(voter2).revealVoteSimple(1, 1, secrets.secret2))
      .to.emit(voting, "VoteRevealed")
      .withArgs(1, voter2.address, 1);

    await expect(voting.connect(delegate).revealVoteSimple(1, 0, secrets.secretDelegate))
      .to.emit(voting, "VoteRevealed")
      .withArgs(1, delegate.address, 0);
  });

  it("Step 8: Advance past reveal deadline and tally", async function () {
    await mine(11);

    await expect(voting.tallyVotes(1))
      .to.emit(voting, "ProposalTallied")
      .withArgs(1);
  });

  it("Step 9: Verify results", async function () {
    expect(await voting.getResult(1, 0)).to.equal(2); // voter1 + delegate
    expect(await voting.getResult(1, 1)).to.equal(1); // voter2
    expect(await voting.getResult(1, 2)).to.equal(0); // abstain

    const proposal = await voting.proposals(1);
    expect(proposal.totalVotes).to.equal(3);
  });

  it("Step 10: Verify delegation power", async function () {
    expect(await delegation.getVotingPower(delegate.address)).to.equal(2); // own + voter3
  });
});
