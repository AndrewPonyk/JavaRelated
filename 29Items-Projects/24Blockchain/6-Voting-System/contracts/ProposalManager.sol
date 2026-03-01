// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

import "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title ProposalManager
 * @notice Manages proposal lifecycle — submission, discussion period,
 *         amendments, and transition to voting.
 */
contract ProposalManager is AccessControl {
    bytes32 public constant MEMBER_ROLE = keccak256("MEMBER_ROLE");

    enum ProposalPhase { Draft, Discussion, Voting, Closed }

    struct Proposal {
        uint256 id;
        address proposer;
        string ipfsCid;
        string title;
        ProposalPhase phase;
        uint256 discussionDeadline;
        uint256 amendmentCount;
        uint256 createdAt;
    }

    struct Amendment {
        address author;
        string ipfsCid;
        uint256 timestamp;
    }

    uint256 public proposalCount;
    uint256 public minDiscussionPeriod = 3 days;
    bool public openSubmission; // when true, any address can submit (no MEMBER_ROLE check)

    mapping(uint256 => Proposal) public proposals;
    mapping(uint256 => Amendment[]) public amendments;

    event ProposalSubmitted(uint256 indexed id, address indexed proposer, string title);
    event AmendmentAdded(uint256 indexed proposalId, uint256 amendmentIndex, address indexed author);
    event ProposalAdvanced(uint256 indexed id, ProposalPhase newPhase);
    event ProposalClosed(uint256 indexed id);

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        openSubmission = true; // default: anyone can submit proposals
    }

    /**
     * @notice Toggle open submission. When false, only MEMBER_ROLE holders can submit.
     */
    function setOpenSubmission(bool _open) external onlyRole(DEFAULT_ADMIN_ROLE) {
        openSubmission = _open;
    }

    /**
     * @notice Grant member role to an address (admin only).
     */
    function addMember(address _member) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _grantRole(MEMBER_ROLE, _member);
    }

    /**
     * @notice Revoke member role from an address (admin only).
     */
    function removeMember(address _member) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _revokeRole(MEMBER_ROLE, _member);
    }

    /**
     * @notice Submit a new proposal for discussion.
     */
    function submitProposal(
        string calldata _title,
        string calldata _ipfsCid,
        uint256 _discussionDuration
    ) external returns (uint256) {
        require(bytes(_title).length > 0, "Title required");
        require(bytes(_ipfsCid).length > 0, "IPFS CID required");
        require(_discussionDuration >= minDiscussionPeriod, "Discussion period too short");

        // Check eligibility: either open submission or caller has MEMBER_ROLE
        require(openSubmission || hasRole(MEMBER_ROLE, msg.sender), "Not a member");

        proposalCount++;
        proposals[proposalCount] = Proposal({
            id: proposalCount,
            proposer: msg.sender,
            ipfsCid: _ipfsCid,
            title: _title,
            phase: ProposalPhase.Discussion,
            discussionDeadline: block.timestamp + _discussionDuration,
            amendmentCount: 0,
            createdAt: block.timestamp
        });

        emit ProposalSubmitted(proposalCount, msg.sender, _title);
        return proposalCount;
    }

    /**
     * @notice Add an amendment to a proposal during discussion phase.
     */
    function addAmendment(uint256 _proposalId, string calldata _ipfsCid) external {
        Proposal storage p = proposals[_proposalId];
        require(p.phase == ProposalPhase.Discussion, "Not in discussion phase");
        require(block.timestamp <= p.discussionDeadline, "Discussion period ended");

        amendments[_proposalId].push(Amendment({
            author: msg.sender,
            ipfsCid: _ipfsCid,
            timestamp: block.timestamp
        }));
        p.amendmentCount++;

        emit AmendmentAdded(_proposalId, p.amendmentCount - 1, msg.sender);
    }

    /**
     * @notice Advance a proposal from discussion to voting phase.
     *         Can only be called after discussion period ends.
     *         Emits ProposalAdvanced event for off-chain coordination
     *         with the VotingSystem contract.
     */
    function advanceToVoting(uint256 _proposalId) external {
        Proposal storage p = proposals[_proposalId];
        require(p.phase == ProposalPhase.Discussion, "Not in discussion phase");
        require(block.timestamp > p.discussionDeadline, "Discussion period not ended");

        p.phase = ProposalPhase.Voting;
        emit ProposalAdvanced(_proposalId, ProposalPhase.Voting);
        // The off-chain backend listens for ProposalAdvanced events and triggers
        // VotingSystem.createProposal() via the blockchain service.
    }

    /**
     * @notice Close a proposal (by proposer or admin).
     */
    function closeProposal(uint256 _proposalId) external {
        Proposal storage p = proposals[_proposalId];
        require(
            msg.sender == p.proposer || hasRole(DEFAULT_ADMIN_ROLE, msg.sender),
            "Not authorized"
        );
        require(p.phase != ProposalPhase.Closed, "Already closed");

        p.phase = ProposalPhase.Closed;
        emit ProposalClosed(_proposalId);
    }

    /**
     * @notice Update the minimum discussion period (admin only).
     */
    function setMinDiscussionPeriod(uint256 _period) external onlyRole(DEFAULT_ADMIN_ROLE) {
        minDiscussionPeriod = _period;
    }

    /**
     * @notice Get amendment details for a proposal.
     */
    function getAmendment(uint256 _proposalId, uint256 _index)
        external view returns (address author, string memory ipfsCid, uint256 timestamp)
    {
        Amendment storage a = amendments[_proposalId][_index];
        return (a.author, a.ipfsCid, a.timestamp);
    }

    /**
     * @notice Get the total number of amendments for a proposal.
     */
    function getAmendmentCount(uint256 _proposalId) external view returns (uint256) {
        return amendments[_proposalId].length;
    }
}
