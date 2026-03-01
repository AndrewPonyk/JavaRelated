const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("DelegationRegistry", function () {
  let delegation;
  let admin, voter1, voter2, voter3, voter4;

  beforeEach(async function () {
    [admin, voter1, voter2, voter3, voter4] = await ethers.getSigners();

    const DelegationRegistry = await ethers.getContractFactory("DelegationRegistry");
    delegation = await DelegationRegistry.deploy();
  });

  describe("delegateVote", function () {
    it("should allow delegation to another address", async function () {
      await expect(delegation.connect(voter1).delegateVote(voter2.address, 0))
        .to.emit(delegation, "VoteDelegated")
        .withArgs(voter1.address, voter2.address, 0);
    });

    it("should allow proposal-specific delegation", async function () {
      await expect(delegation.connect(voter1).delegateVote(voter2.address, 5))
        .to.emit(delegation, "VoteDelegated")
        .withArgs(voter1.address, voter2.address, 5);
    });

    it("should reject delegation to self", async function () {
      await expect(
        delegation.connect(voter1).delegateVote(voter1.address, 0)
      ).to.be.revertedWith("Cannot delegate to self");
    });

    it("should reject delegation to zero address", async function () {
      await expect(
        delegation.connect(voter1).delegateVote(ethers.ZeroAddress, 0)
      ).to.be.revertedWith("Cannot delegate to zero address");
    });

    it("should prevent circular delegation", async function () {
      await delegation.connect(voter2).delegateVote(voter1.address, 0);
      await expect(
        delegation.connect(voter1).delegateVote(voter2.address, 0)
      ).to.be.revertedWith("Circular delegation");
    });

    it("should replace existing delegation", async function () {
      await delegation.connect(voter1).delegateVote(voter2.address, 0);
      const tx = await delegation.connect(voter1).delegateVote(voter3.address, 0);

      await expect(tx)
        .to.emit(delegation, "DelegationRevoked")
        .withArgs(voter1.address, voter2.address);
      await expect(tx)
        .to.emit(delegation, "VoteDelegated")
        .withArgs(voter1.address, voter3.address, 0);
    });
  });

  describe("revokeDelegation", function () {
    it("should allow revoking an active delegation", async function () {
      await delegation.connect(voter1).delegateVote(voter2.address, 0);
      await expect(delegation.connect(voter1).revokeDelegation(0))
        .to.emit(delegation, "DelegationRevoked")
        .withArgs(voter1.address, voter2.address);
    });

    it("should reject revoking when no delegation exists", async function () {
      await expect(
        delegation.connect(voter1).revokeDelegation(0)
      ).to.be.revertedWith("No active delegation");
    });

    it("should allow re-delegation after revocation", async function () {
      await delegation.connect(voter1).delegateVote(voter2.address, 0);
      await delegation.connect(voter1).revokeDelegation(0);

      await expect(delegation.connect(voter1).delegateVote(voter3.address, 0))
        .to.emit(delegation, "VoteDelegated")
        .withArgs(voter1.address, voter3.address, 0);
    });
  });

  describe("getEffectiveDelegate", function () {
    it("should return zero address when no delegation", async function () {
      expect(await delegation.getEffectiveDelegate(voter1.address, 1)).to.equal(ethers.ZeroAddress);
    });

    it("should return global delegate when no proposal-specific", async function () {
      await delegation.connect(voter1).delegateVote(voter2.address, 0); // global
      expect(await delegation.getEffectiveDelegate(voter1.address, 5)).to.equal(voter2.address);
    });

    it("should return proposal-specific delegate over global", async function () {
      await delegation.connect(voter1).delegateVote(voter2.address, 0); // global
      await delegation.connect(voter1).delegateVote(voter3.address, 1); // proposal 1

      expect(await delegation.getEffectiveDelegate(voter1.address, 1)).to.equal(voter3.address);
      expect(await delegation.getEffectiveDelegate(voter1.address, 2)).to.equal(voter2.address); // falls back to global
    });
  });

  describe("getVotingPower", function () {
    it("should return 1 for address with no delegators", async function () {
      expect(await delegation.getVotingPower(voter1.address)).to.equal(1);
    });

    it("should increase with delegators", async function () {
      await delegation.connect(voter2).delegateVote(voter1.address, 0);
      await delegation.connect(voter3).delegateVote(voter1.address, 0);

      expect(await delegation.getVotingPower(voter1.address)).to.equal(3); // 1 own + 2 delegators
    });

    it("should decrease when delegator revokes", async function () {
      await delegation.connect(voter2).delegateVote(voter1.address, 0);
      await delegation.connect(voter3).delegateVote(voter1.address, 0);

      await delegation.connect(voter2).revokeDelegation(0);

      expect(await delegation.getVotingPower(voter1.address)).to.equal(2); // 1 own + 1 delegator
    });
  });

  describe("getVotingPowerForProposal", function () {
    it("should count proposal-specific delegators", async function () {
      await delegation.connect(voter2).delegateVote(voter1.address, 5); // proposal 5
      await delegation.connect(voter3).delegateVote(voter1.address, 0); // global

      expect(await delegation.getVotingPowerForProposal(voter1.address, 5)).to.equal(3); // 1 own + voter2 (specific) + voter3 (global)
    });
  });

  describe("hasDelegated", function () {
    it("should return false when no delegation", async function () {
      expect(await delegation.hasDelegated(voter1.address, 0)).to.be.false;
    });

    it("should return true for global delegation", async function () {
      await delegation.connect(voter1).delegateVote(voter2.address, 0);
      expect(await delegation.hasDelegated(voter1.address, 5)).to.be.true;
    });

    it("should return true for proposal-specific delegation", async function () {
      await delegation.connect(voter1).delegateVote(voter2.address, 5);
      expect(await delegation.hasDelegated(voter1.address, 5)).to.be.true;
    });
  });

  describe("getDelegators", function () {
    it("should return empty array when no delegators", async function () {
      const delegators = await delegation.getDelegators(voter1.address);
      expect(delegators).to.be.an("array").that.is.empty;
    });

    it("should return list of delegators", async function () {
      await delegation.connect(voter2).delegateVote(voter1.address, 0);
      await delegation.connect(voter3).delegateVote(voter1.address, 0);

      const delegators = await delegation.getDelegators(voter1.address);
      expect(delegators).to.have.lengthOf(2);
      expect(delegators).to.include(voter2.address);
      expect(delegators).to.include(voter3.address);
    });
  });
});
