const { expect } = require("chai");
const { ethers } = require("hardhat");
const { time } = require("@nomicfoundation/hardhat-network-helpers");

describe("ProposalManager", function () {
  let manager;
  let admin, proposer, member, outsider;

  const THREE_DAYS = 3 * 24 * 60 * 60;

  beforeEach(async function () {
    [admin, proposer, member, outsider] = await ethers.getSigners();

    const ProposalManager = await ethers.getContractFactory("ProposalManager");
    manager = await ProposalManager.deploy();
  });

  describe("constructor", function () {
    it("should set admin role", async function () {
      const DEFAULT_ADMIN_ROLE = ethers.ZeroHash;
      expect(await manager.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.be.true;
    });

    it("should enable open submission by default", async function () {
      expect(await manager.openSubmission()).to.be.true;
    });

    it("should set minimum discussion period to 3 days", async function () {
      expect(await manager.minDiscussionPeriod()).to.equal(THREE_DAYS);
    });
  });

  describe("submitProposal", function () {
    it("should submit a proposal with valid params", async function () {
      const duration = THREE_DAYS + 1;
      const tx = await manager.connect(proposer).submitProposal("Test Proposal", "QmCid123", duration);

      await expect(tx)
        .to.emit(manager, "ProposalSubmitted")
        .withArgs(1, proposer.address, "Test Proposal");

      const proposal = await manager.proposals(1);
      expect(proposal.title).to.equal("Test Proposal");
      expect(proposal.ipfsCid).to.equal("QmCid123");
      expect(proposal.proposer).to.equal(proposer.address);
      expect(proposal.phase).to.equal(1); // Discussion
    });

    it("should reject proposal with empty title", async function () {
      await expect(
        manager.connect(proposer).submitProposal("", "QmCid123", THREE_DAYS + 1)
      ).to.be.revertedWith("Title required");
    });

    it("should reject proposal with empty IPFS CID", async function () {
      await expect(
        manager.connect(proposer).submitProposal("Test", "", THREE_DAYS + 1)
      ).to.be.revertedWith("IPFS CID required");
    });

    it("should reject proposal with too short discussion period", async function () {
      await expect(
        manager.connect(proposer).submitProposal("Test", "QmCid123", 1000)
      ).to.be.revertedWith("Discussion period too short");
    });

    it("should reject non-member when open submission is off", async function () {
      await manager.setOpenSubmission(false);
      await expect(
        manager.connect(outsider).submitProposal("Test", "QmCid", THREE_DAYS + 1)
      ).to.be.revertedWith("Not a member");
    });

    it("should allow member when open submission is off", async function () {
      await manager.setOpenSubmission(false);
      await manager.addMember(member.address);

      await expect(
        manager.connect(member).submitProposal("Member Proposal", "QmCid", THREE_DAYS + 1)
      ).to.emit(manager, "ProposalSubmitted");
    });

    it("should increment proposal count", async function () {
      await manager.connect(proposer).submitProposal("P1", "QmCid1", THREE_DAYS + 1);
      await manager.connect(proposer).submitProposal("P2", "QmCid2", THREE_DAYS + 1);
      expect(await manager.proposalCount()).to.equal(2);
    });
  });

  describe("addAmendment", function () {
    beforeEach(async function () {
      await manager.connect(proposer).submitProposal("Test", "QmCid123", THREE_DAYS + 1);
    });

    it("should allow amendments during discussion phase", async function () {
      await expect(manager.connect(member).addAmendment(1, "QmAmendmentCid"))
        .to.emit(manager, "AmendmentAdded")
        .withArgs(1, member.address);
    });

    it("should track amendment count", async function () {
      await manager.connect(member).addAmendment(1, "QmA1");
      await manager.connect(proposer).addAmendment(1, "QmA2");
      expect(await manager.getAmendmentCount(1)).to.equal(2);
    });

    it("should store amendment details", async function () {
      await manager.connect(member).addAmendment(1, "QmAmendCid");
      const [author, ipfsCid] = await manager.getAmendment(1, 0);
      expect(author).to.equal(member.address);
      expect(ipfsCid).to.equal("QmAmendCid");
    });
  });

  describe("advanceToVoting", function () {
    it("should advance after discussion period ends", async function () {
      await manager.connect(proposer).submitProposal("Test", "QmCid", THREE_DAYS + 1);

      // Advance time past discussion deadline
      await time.increase(THREE_DAYS + 2);

      await expect(manager.connect(proposer).advanceToVoting(1))
        .to.emit(manager, "ProposalAdvanced")
        .withArgs(1, 2); // Voting phase

      const proposal = await manager.proposals(1);
      expect(proposal.phase).to.equal(2); // Voting
    });

    it("should reject advance before discussion ends", async function () {
      await manager.connect(proposer).submitProposal("Test", "QmCid", THREE_DAYS + 1);

      await expect(
        manager.connect(proposer).advanceToVoting(1)
      ).to.be.revertedWith("Discussion period not ended");
    });
  });

  describe("closeProposal", function () {
    beforeEach(async function () {
      await manager.connect(proposer).submitProposal("Test", "QmCid", THREE_DAYS + 1);
    });

    it("should allow proposer to close", async function () {
      await expect(manager.connect(proposer).closeProposal(1))
        .to.emit(manager, "ProposalClosed")
        .withArgs(1);
    });

    it("should allow admin to close", async function () {
      await expect(manager.closeProposal(1))
        .to.emit(manager, "ProposalClosed")
        .withArgs(1);
    });

    it("should reject close from unauthorized address", async function () {
      await expect(
        manager.connect(outsider).closeProposal(1)
      ).to.be.revertedWith("Not authorized");
    });

    it("should reject double close", async function () {
      await manager.connect(proposer).closeProposal(1);
      await expect(
        manager.connect(proposer).closeProposal(1)
      ).to.be.revertedWith("Already closed");
    });
  });

  describe("admin functions", function () {
    it("should update min discussion period", async function () {
      const sevenDays = 7 * 24 * 60 * 60;
      await manager.setMinDiscussionPeriod(sevenDays);
      expect(await manager.minDiscussionPeriod()).to.equal(sevenDays);
    });

    it("should add and remove members", async function () {
      const MEMBER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MEMBER_ROLE"));
      await manager.addMember(member.address);
      expect(await manager.hasRole(MEMBER_ROLE, member.address)).to.be.true;

      await manager.removeMember(member.address);
      expect(await manager.hasRole(MEMBER_ROLE, member.address)).to.be.false;
    });
  });
});
