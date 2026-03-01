const { expect } = require("chai");
const { ethers } = require("hardhat");
const { mine } = require("@nomicfoundation/hardhat-network-helpers");

describe("VotingSystem", function () {
  let voting, delegation;
  let admin, voter1, voter2, voter3;

  beforeEach(async function () {
    [admin, voter1, voter2, voter3] = await ethers.getSigners();

    const DelegationRegistry = await ethers.getContractFactory("DelegationRegistry");
    delegation = await DelegationRegistry.deploy();

    const VotingSystem = await ethers.getContractFactory("VotingSystem");
    voting = await VotingSystem.deploy(await delegation.getAddress());
  });

  describe("constructor", function () {
    it("should set admin roles", async function () {
      const DEFAULT_ADMIN_ROLE = ethers.ZeroHash;
      expect(await voting.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.be.true;
    });

    it("should enable open voting by default", async function () {
      expect(await voting.openVoting()).to.be.true;
    });

    it("should link delegation registry", async function () {
      expect(await voting.delegationRegistry()).to.equal(await delegation.getAddress());
    });
  });

  describe("createProposal", function () {
    it("should create a proposal with valid parameters", async function () {
      const tx = await voting.connect(voter1).createProposal("QmTestCid123", 100, 100, 3);

      await expect(tx)
        .to.emit(voting, "ProposalCreated")
        .withArgs(1, voter1.address, "QmTestCid123");

      const proposal = await voting.proposals(1);
      expect(proposal.ipfsCid).to.equal("QmTestCid123");
      expect(proposal.creator).to.equal(voter1.address);
      expect(proposal.optionCount).to.equal(3);
      expect(proposal.state).to.equal(1); // CommitPhase
    });

    it("should reject proposals with fewer than 2 options", async function () {
      await expect(
        voting.connect(voter1).createProposal("QmTestCid", 100, 100, 1)
      ).to.be.revertedWith("Need at least 2 options");
    });

    it("should reject proposals with zero duration", async function () {
      await expect(
        voting.connect(voter1).createProposal("QmTestCid", 0, 100, 2)
      ).to.be.revertedWith("Durations must be > 0");
    });

    it("should increment proposal count", async function () {
      await voting.connect(voter1).createProposal("QmCid1", 100, 100, 2);
      await voting.connect(voter2).createProposal("QmCid2", 100, 100, 2);
      expect(await voting.proposalCount()).to.equal(2);
    });
  });

  describe("commitVote", function () {
    let proposalId;
    let secret;

    beforeEach(async function () {
      secret = ethers.hexlify(ethers.randomBytes(32));
      const tx = await voting.createProposal("QmTestCid", 1000, 1000, 2);
      const receipt = await tx.wait();
      const event = receipt.logs.find((l) => l.fragment?.name === "ProposalCreated");
      proposalId = event.args.proposalId;
    });

    it("should accept a valid commitment", async function () {
      const commitHash = await voting.getCommitHash(proposalId, 0, secret);
      await expect(voting.connect(voter1).commitVote(proposalId, commitHash))
        .to.emit(voting, "VoteCommitted")
        .withArgs(proposalId, voter1.address);
    });

    it("should reject double commitment from same voter", async function () {
      const commitHash = await voting.getCommitHash(proposalId, 0, secret);
      await voting.connect(voter1).commitVote(proposalId, commitHash);

      await expect(
        voting.connect(voter1).commitVote(proposalId, commitHash)
      ).to.be.revertedWith("Already committed");
    });

    it("should allow multiple voters to commit", async function () {
      const hash1 = await voting.getCommitHash(proposalId, 0, secret);
      const secret2 = ethers.hexlify(ethers.randomBytes(32));
      const hash2 = await voting.getCommitHash(proposalId, 1, secret2);

      await voting.connect(voter1).commitVote(proposalId, hash1);
      await voting.connect(voter2).commitVote(proposalId, hash2);

      const commitment1 = await voting.commitments(proposalId, voter1.address);
      const commitment2 = await voting.commitments(proposalId, voter2.address);
      expect(commitment1.committed).to.be.true;
      expect(commitment2.committed).to.be.true;
    });

    it("should reject vote from delegated address", async function () {
      // voter1 delegates to voter2
      await delegation.connect(voter1).delegateVote(voter2.address, 0);

      const commitHash = await voting.getCommitHash(proposalId, 0, secret);
      await expect(
        voting.connect(voter1).commitVote(proposalId, commitHash)
      ).to.be.revertedWith("Vote delegated to another address");
    });

    it("should reject when not in commit phase", async function () {
      await voting.cancelProposal(proposalId);
      const commitHash = await voting.getCommitHash(proposalId, 0, secret);
      await expect(
        voting.connect(voter1).commitVote(proposalId, commitHash)
      ).to.be.revertedWith("Not in commit phase");
    });

    it("should reject if open voting is off and voter has no role", async function () {
      await voting.setOpenVoting(false);
      const commitHash = await voting.getCommitHash(proposalId, 0, secret);
      await expect(
        voting.connect(voter1).commitVote(proposalId, commitHash)
      ).to.be.revertedWith("Not eligible to vote");
    });
  });

  describe("revealVoteSimple", function () {
    let proposalId;
    let secret;
    const choice = 0;

    beforeEach(async function () {
      secret = ethers.hexlify(ethers.randomBytes(32));
      // Create proposal with short commit phase (5 blocks)
      const tx = await voting.createProposal("QmTestCid", 5, 1000, 2);
      const receipt = await tx.wait();
      const event = receipt.logs.find((l) => l.fragment?.name === "ProposalCreated");
      proposalId = event.args.proposalId;

      // Commit a vote
      const commitHash = await voting.getCommitHash(proposalId, choice, secret);
      await voting.connect(voter1).commitVote(proposalId, commitHash);
    });

    it("should reveal a committed vote after commit phase", async function () {
      await mine(6);

      await expect(voting.connect(voter1).revealVoteSimple(proposalId, choice, secret))
        .to.emit(voting, "VoteRevealed")
        .withArgs(proposalId, voter1.address, choice);
    });

    it("should reject reveal with wrong secret", async function () {
      await mine(6);

      const wrongSecret = ethers.hexlify(ethers.randomBytes(32));
      await expect(
        voting.connect(voter1).revealVoteSimple(proposalId, choice, wrongSecret)
      ).to.be.revertedWith("Commitment mismatch");
    });

    it("should reject reveal with wrong choice", async function () {
      await mine(6);

      await expect(
        voting.connect(voter1).revealVoteSimple(proposalId, 1, secret)
      ).to.be.revertedWith("Commitment mismatch");
    });

    it("should reject double reveal", async function () {
      await mine(6);

      await voting.connect(voter1).revealVoteSimple(proposalId, choice, secret);
      await expect(
        voting.connect(voter1).revealVoteSimple(proposalId, choice, secret)
      ).to.be.revertedWith("Already revealed");
    });

    it("should reject reveal from non-committer", async function () {
      await mine(6);

      await expect(
        voting.connect(voter2).revealVoteSimple(proposalId, choice, secret)
      ).to.be.revertedWith("No commitment found");
    });
  });

  describe("tallyVotes", function () {
    it("should tally after reveal phase ends", async function () {
      const secret = ethers.hexlify(ethers.randomBytes(32));
      const tx = await voting.createProposal("QmTally", 3, 3, 2);
      const receipt = await tx.wait();
      const event = receipt.logs.find((l) => l.fragment?.name === "ProposalCreated");
      const proposalId = event.args.proposalId;

      // Commit
      const commitHash = await voting.getCommitHash(proposalId, 0, secret);
      await voting.connect(voter1).commitVote(proposalId, commitHash);

      // Advance past commit deadline
      await mine(4);

      // Reveal
      await voting.connect(voter1).revealVoteSimple(proposalId, 0, secret);

      // Advance past reveal deadline
      await mine(4);

      // Tally
      await expect(voting.tallyVotes(proposalId))
        .to.emit(voting, "ProposalTallied")
        .withArgs(proposalId);

      // Check result
      expect(await voting.getResult(proposalId, 0)).to.equal(1);
    });

    it("should reject tally before reveal phase ends", async function () {
      const tx = await voting.createProposal("QmTally", 1000, 1000, 2);
      const receipt = await tx.wait();
      const event = receipt.logs.find((l) => l.fragment?.name === "ProposalCreated");
      const proposalId = event.args.proposalId;

      await expect(voting.tallyVotes(proposalId)).to.be.revertedWith("Reveal phase not ended");
    });
  });

  describe("cancelProposal", function () {
    it("should allow admin to cancel", async function () {
      await voting.createProposal("QmCancel", 100, 100, 2);
      await expect(voting.cancelProposal(1))
        .to.emit(voting, "ProposalCancelled")
        .withArgs(1);
    });

    it("should reject cancel from non-admin", async function () {
      await voting.createProposal("QmCancel", 100, 100, 2);
      await expect(voting.connect(voter1).cancelProposal(1)).to.be.reverted;
    });

    it("should reject cancel of tallied proposal", async function () {
      const secret = ethers.hexlify(ethers.randomBytes(32));
      const tx = await voting.createProposal("QmCancel", 3, 3, 2);
      const receipt = await tx.wait();
      const event = receipt.logs.find((l) => l.fragment?.name === "ProposalCreated");
      const proposalId = event.args.proposalId;

      // Advance past both deadlines
      await mine(8);
      await voting.tallyVotes(proposalId);

      await expect(voting.cancelProposal(proposalId)).to.be.revertedWith("Cannot cancel");
    });
  });

  describe("getCommitHash", function () {
    it("should return deterministic hash", async function () {
      const secret = ethers.hexlify(ethers.randomBytes(32));
      const hash1 = await voting.getCommitHash(1, 0, secret);
      const hash2 = await voting.getCommitHash(1, 0, secret);
      expect(hash1).to.equal(hash2);
    });

    it("should return different hash for different inputs", async function () {
      const secret = ethers.hexlify(ethers.randomBytes(32));
      const hash1 = await voting.getCommitHash(1, 0, secret);
      const hash2 = await voting.getCommitHash(1, 1, secret);
      expect(hash1).to.not.equal(hash2);
    });
  });

  describe("admin functions", function () {
    it("should toggle open voting", async function () {
      expect(await voting.openVoting()).to.be.true;
      await voting.setOpenVoting(false);
      expect(await voting.openVoting()).to.be.false;
    });

    it("should set delegation registry", async function () {
      const DelegationRegistry = await ethers.getContractFactory("DelegationRegistry");
      const newRegistry = await DelegationRegistry.deploy();
      await voting.setDelegationRegistry(await newRegistry.getAddress());
      expect(await voting.delegationRegistry()).to.equal(await newRegistry.getAddress());
    });
  });
});
