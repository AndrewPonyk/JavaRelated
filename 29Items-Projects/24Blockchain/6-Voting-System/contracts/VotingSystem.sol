// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";

interface IZkVerifier {
    function verifyProof(
        uint256[2] calldata a,
        uint256[2][2] calldata b,
        uint256[2] calldata c,
        uint256[3] calldata input
    ) external view returns (bool);
}

interface IDelegationRegistry {
    function getEffectiveDelegate(address voter, uint256 proposalId) external view returns (address);
}

/**
 * @title VotingSystem
 * @notice Commit-reveal voting with zkSNARK anonymous verification.
 *         Voters commit a hash of (choice + secret) during the commit phase,
 *         then reveal the choice and secret during the reveal phase.
 */
contract VotingSystem is AccessControl, ReentrancyGuard {
    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant VOTER_ROLE = keccak256("VOTER_ROLE");

    enum ProposalState { Created, CommitPhase, RevealPhase, Tallied, Cancelled }

    struct Proposal {
        uint256 id;
        string ipfsCid;
        address creator;
        uint256 commitDeadline;
        uint256 revealDeadline;
        uint256 optionCount;
        ProposalState state;
        mapping(uint256 => uint256) tally;
        uint256 totalVotes;
    }

    struct Commitment {
        bytes32 commitHash;
        bool revealed;
        bool committed;
    }

    IZkVerifier public zkVerifier;
    IDelegationRegistry public delegationRegistry;
    bool public zkVerificationEnabled;
    bool public openVoting; // when true, any address can vote (no VOTER_ROLE check)

    uint256 public proposalCount;
    mapping(uint256 => Proposal) public proposals;
    mapping(uint256 => mapping(address => Commitment)) public commitments;

    event ProposalCreated(uint256 indexed proposalId, address indexed creator, string ipfsCid);
    event VoteCommitted(uint256 indexed proposalId, address indexed voter);
    event VoteRevealed(uint256 indexed proposalId, address indexed voter, uint256 choice);
    event ProposalTallied(uint256 indexed proposalId);
    event ProposalCancelled(uint256 indexed proposalId);
    event ZkVerifierUpdated(address indexed verifier);
    event DelegationRegistryUpdated(address indexed registry);

    constructor(address _delegationRegistry) {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
        openVoting = true; // default: anyone can vote

        if (_delegationRegistry != address(0)) {
            delegationRegistry = IDelegationRegistry(_delegationRegistry);
        }
    }

    /**
     * @notice Set the zkSNARK verifier contract address.
     */
    function setZkVerifier(address _verifier) external onlyRole(ADMIN_ROLE) {
        zkVerifier = IZkVerifier(_verifier);
        zkVerificationEnabled = _verifier != address(0);
        emit ZkVerifierUpdated(_verifier);
    }

    /**
     * @notice Set the delegation registry contract address.
     */
    function setDelegationRegistry(address _registry) external onlyRole(ADMIN_ROLE) {
        delegationRegistry = IDelegationRegistry(_registry);
        emit DelegationRegistryUpdated(_registry);
    }

    /**
     * @notice Toggle open voting. When false, only VOTER_ROLE holders can vote.
     */
    function setOpenVoting(bool _open) external onlyRole(ADMIN_ROLE) {
        openVoting = _open;
    }

    /**
     * @notice Create a new proposal and set voting period.
     */
    function createProposal(
        string calldata _ipfsCid,
        uint256 _commitDuration,
        uint256 _revealDuration,
        uint256 _optionCount
    ) external returns (uint256) {
        require(_optionCount >= 2, "Need at least 2 options");
        require(_commitDuration > 0 && _revealDuration > 0, "Durations must be > 0");

        proposalCount++;
        Proposal storage p = proposals[proposalCount];
        p.id = proposalCount;
        p.ipfsCid = _ipfsCid;
        p.creator = msg.sender;
        p.commitDeadline = block.number + _commitDuration;
        p.revealDeadline = block.number + _commitDuration + _revealDuration;
        p.optionCount = _optionCount;
        p.state = ProposalState.CommitPhase;

        emit ProposalCreated(proposalCount, msg.sender, _ipfsCid);
        return proposalCount;
    }

    /**
     * @notice Commit a vote hash. hash = keccak256(abi.encodePacked(proposalId, choice, secret))
     */
    function commitVote(uint256 _proposalId, bytes32 _commitHash) external {
        Proposal storage p = proposals[_proposalId];
        require(p.state == ProposalState.CommitPhase, "Not in commit phase");
        require(block.number <= p.commitDeadline, "Commit phase ended");
        require(!commitments[_proposalId][msg.sender].committed, "Already committed");

        // Check voter eligibility: either open voting or caller has VOTER_ROLE
        require(openVoting || hasRole(VOTER_ROLE, msg.sender), "Not eligible to vote");

        // If delegation registry is set, ensure the voter has not delegated their vote
        if (address(delegationRegistry) != address(0)) {
            address effectiveDelegate = delegationRegistry.getEffectiveDelegate(msg.sender, _proposalId);
            require(effectiveDelegate == address(0), "Vote delegated to another address");
        }

        commitments[_proposalId][msg.sender] = Commitment({
            commitHash: _commitHash,
            revealed: false,
            committed: true
        });

        emit VoteCommitted(_proposalId, msg.sender);
    }

    /**
     * @notice Reveal a previously committed vote.
     * @param _proposalId The proposal
     * @param _choice The voting option index
     * @param _secret The secret used in the commitment
     * @param _zkProofA Part A of groth16 proof (pass [0,0] if zk verification disabled)
     * @param _zkProofB Part B of groth16 proof
     * @param _zkProofC Part C of groth16 proof
     * @param _zkPublicInputs Public inputs for zk verification
     */
    function revealVote(
        uint256 _proposalId,
        uint256 _choice,
        bytes32 _secret,
        uint256[2] calldata _zkProofA,
        uint256[2][2] calldata _zkProofB,
        uint256[2] calldata _zkProofC,
        uint256[3] calldata _zkPublicInputs
    ) external nonReentrant {
        Proposal storage p = proposals[_proposalId];
        require(p.state == ProposalState.CommitPhase || p.state == ProposalState.RevealPhase, "Not in voting phase");

        // Transition to reveal phase if commit deadline passed
        if (block.number > p.commitDeadline && p.state == ProposalState.CommitPhase) {
            p.state = ProposalState.RevealPhase;
        }
        require(p.state == ProposalState.RevealPhase, "Reveal phase not started");
        require(block.number <= p.revealDeadline, "Reveal phase ended");
        require(_choice < p.optionCount, "Invalid choice");

        Commitment storage c = commitments[_proposalId][msg.sender];
        require(c.committed, "No commitment found");
        require(!c.revealed, "Already revealed");

        // Verify commitment hash matches
        bytes32 expectedHash = keccak256(abi.encodePacked(_proposalId, _choice, _secret));
        require(expectedHash == c.commitHash, "Commitment mismatch");

        // Verify zkSNARK proof if verification is enabled
        if (zkVerificationEnabled) {
            require(
                zkVerifier.verifyProof(_zkProofA, _zkProofB, _zkProofC, _zkPublicInputs),
                "Invalid zk proof"
            );
        }

        c.revealed = true;
        p.tally[_choice]++;
        p.totalVotes++;

        emit VoteRevealed(_proposalId, msg.sender, _choice);
    }

    /**
     * @notice Simplified reveal without zk proof (when zk verification is disabled).
     */
    function revealVoteSimple(
        uint256 _proposalId,
        uint256 _choice,
        bytes32 _secret
    ) external nonReentrant {
        require(!zkVerificationEnabled, "Use revealVote with zk proof");

        Proposal storage p = proposals[_proposalId];
        require(p.state == ProposalState.CommitPhase || p.state == ProposalState.RevealPhase, "Not in voting phase");

        if (block.number > p.commitDeadline && p.state == ProposalState.CommitPhase) {
            p.state = ProposalState.RevealPhase;
        }
        require(p.state == ProposalState.RevealPhase, "Reveal phase not started");
        require(block.number <= p.revealDeadline, "Reveal phase ended");
        require(_choice < p.optionCount, "Invalid choice");

        Commitment storage c = commitments[_proposalId][msg.sender];
        require(c.committed, "No commitment found");
        require(!c.revealed, "Already revealed");

        bytes32 expectedHash = keccak256(abi.encodePacked(_proposalId, _choice, _secret));
        require(expectedHash == c.commitHash, "Commitment mismatch");

        c.revealed = true;
        p.tally[_choice]++;
        p.totalVotes++;

        emit VoteRevealed(_proposalId, msg.sender, _choice);
    }

    /**
     * @notice Finalize the proposal after reveal phase ends.
     */
    function tallyVotes(uint256 _proposalId) external {
        Proposal storage p = proposals[_proposalId];
        require(block.number > p.revealDeadline, "Reveal phase not ended");
        require(p.state == ProposalState.RevealPhase || p.state == ProposalState.CommitPhase, "Cannot tally");

        p.state = ProposalState.Tallied;
        emit ProposalTallied(_proposalId);
    }

    /**
     * @notice Cancel a proposal (admin only).
     */
    function cancelProposal(uint256 _proposalId) external onlyRole(ADMIN_ROLE) {
        Proposal storage p = proposals[_proposalId];
        require(p.state != ProposalState.Tallied && p.state != ProposalState.Cancelled, "Cannot cancel");
        p.state = ProposalState.Cancelled;
        emit ProposalCancelled(_proposalId);
    }

    /**
     * @notice Get the tally for a specific option after voting is tallied.
     */
    function getResult(uint256 _proposalId, uint256 _optionIndex) external view returns (uint256) {
        Proposal storage p = proposals[_proposalId];
        require(p.state == ProposalState.Tallied, "Not tallied yet");
        require(_optionIndex < p.optionCount, "Invalid option");
        return p.tally[_optionIndex];
    }

    /**
     * @notice Helper to generate commitment hash (for testing and frontend).
     */
    function getCommitHash(
        uint256 _proposalId,
        uint256 _choice,
        bytes32 _secret
    ) external pure returns (bytes32) {
        return keccak256(abi.encodePacked(_proposalId, _choice, _secret));
    }
}
