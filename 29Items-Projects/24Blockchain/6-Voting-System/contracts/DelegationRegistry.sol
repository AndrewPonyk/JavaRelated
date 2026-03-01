// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

import "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title DelegationRegistry
 * @notice Allows members to delegate their voting power to trusted
 *         representatives. Delegation can be revoked at any time before
 *         the delegate casts a vote.
 */
contract DelegationRegistry is AccessControl {

    struct Delegation {
        address delegate;
        uint256 proposalId; // 0 = global delegation, >0 = proposal-specific
        uint256 timestamp;
        bool active;
    }

    // voter => proposalId => Delegation (proposalId=0 means global)
    mapping(address => mapping(uint256 => Delegation)) public delegations;

    // delegate => list of delegators (for calculating effective voting power)
    mapping(address => address[]) public delegators;

    // Track delegator index for O(1) removal
    mapping(address => mapping(address => uint256)) private delegatorIndex;

    // Track whether a delegator exists in the array (to avoid false positives from index 0)
    mapping(address => mapping(address => bool)) private isDelegator;

    event VoteDelegated(address indexed from, address indexed to, uint256 proposalId);
    event DelegationRevoked(address indexed from, address indexed previousDelegate, uint256 proposalId);

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
    }

    /**
     * @notice Delegate voting power to another address.
     * @param _delegate The address to delegate to
     * @param _proposalId Proposal ID (0 for global delegation)
     */
    function delegateVote(address _delegate, uint256 _proposalId) external {
        require(_delegate != address(0), "Cannot delegate to zero address");
        require(_delegate != msg.sender, "Cannot delegate to self");

        // Check for circular delegation (one level deep)
        Delegation storage theirDelegation = delegations[_delegate][_proposalId];
        if (theirDelegation.active) {
            require(theirDelegation.delegate != msg.sender, "Circular delegation");
        }
        // Also check global delegation for circular reference
        if (_proposalId != 0) {
            Delegation storage theirGlobal = delegations[_delegate][0];
            if (theirGlobal.active) {
                require(theirGlobal.delegate != msg.sender, "Circular delegation via global");
            }
        }

        // Revoke existing delegation if any
        Delegation storage existing = delegations[msg.sender][_proposalId];
        if (existing.active) {
            _removeDelegator(existing.delegate, msg.sender);
            emit DelegationRevoked(msg.sender, existing.delegate, _proposalId);
        }

        delegations[msg.sender][_proposalId] = Delegation({
            delegate: _delegate,
            proposalId: _proposalId,
            timestamp: block.timestamp,
            active: true
        });

        // Track delegator for voting power calculation
        delegatorIndex[_delegate][msg.sender] = delegators[_delegate].length;
        isDelegator[_delegate][msg.sender] = true;
        delegators[_delegate].push(msg.sender);

        emit VoteDelegated(msg.sender, _delegate, _proposalId);
    }

    /**
     * @notice Revoke a delegation.
     * @param _proposalId The proposal ID (0 for global)
     */
    function revokeDelegation(uint256 _proposalId) external {
        Delegation storage d = delegations[msg.sender][_proposalId];
        require(d.active, "No active delegation");

        address previousDelegate = d.delegate;
        _removeDelegator(previousDelegate, msg.sender);
        d.active = false;

        emit DelegationRevoked(msg.sender, previousDelegate, _proposalId);
    }

    /**
     * @notice Get the effective delegate for a voter on a given proposal.
     *         Checks proposal-specific first, then falls back to global.
     */
    function getEffectiveDelegate(address _voter, uint256 _proposalId)
        external view returns (address)
    {
        // Check proposal-specific delegation first
        if (_proposalId != 0) {
            Delegation storage specific = delegations[_voter][_proposalId];
            if (specific.active) {
                return specific.delegate;
            }
        }

        // Fall back to global delegation
        Delegation storage globalDel = delegations[_voter][0];
        if (globalDel.active) {
            return globalDel.delegate;
        }

        return address(0);
    }

    /**
     * @notice Get the effective voting power of a delegate.
     *         Returns 1 (own vote) + number of active delegators.
     *         Counts both global and proposal-specific delegators.
     */
    function getVotingPower(address _delegate) external view returns (uint256) {
        // Count active delegators by checking the delegators array
        // Each address in this array actively delegated to _delegate
        // (either globally or for a specific proposal)
        uint256 activeDelegators = 0;
        uint256 len = delegators[_delegate].length;
        for (uint256 i = 0; i < len; i++) {
            if (isDelegator[_delegate][delegators[_delegate][i]]) {
                activeDelegators++;
            }
        }
        return 1 + activeDelegators;
    }

    /**
     * @notice Get voting power for a specific proposal.
     *         Counts delegators with proposal-specific or global delegation.
     */
    function getVotingPowerForProposal(address _delegate, uint256 _proposalId)
        external view returns (uint256)
    {
        uint256 power = 1; // own vote
        uint256 len = delegators[_delegate].length;

        for (uint256 i = 0; i < len; i++) {
            address delegator = delegators[_delegate][i];
            if (!isDelegator[_delegate][delegator]) continue;

            // Check if this delegator has a proposal-specific delegation to _delegate
            Delegation storage specific = delegations[delegator][_proposalId];
            if (specific.active && specific.delegate == _delegate) {
                power++;
                continue;
            }

            // Check if they have a global delegation to _delegate
            Delegation storage globalDel = delegations[delegator][0];
            if (globalDel.active && globalDel.delegate == _delegate) {
                // Only count global if no proposal-specific delegation exists
                // (proposal-specific to someone else would override)
                if (!specific.active) {
                    power++;
                }
            }
        }

        return power;
    }

    /**
     * @notice Get all addresses that have delegated to a delegate.
     */
    function getDelegators(address _delegate) external view returns (address[] memory) {
        return delegators[_delegate];
    }

    /**
     * @notice Check if a voter has an active delegation for a proposal.
     */
    function hasDelegated(address _voter, uint256 _proposalId) external view returns (bool) {
        Delegation storage specific = delegations[_voter][_proposalId];
        if (specific.active) return true;
        Delegation storage globalDel = delegations[_voter][0];
        return globalDel.active;
    }

    // --- Internal ---

    function _removeDelegator(address _delegate, address _delegator) internal {
        if (!isDelegator[_delegate][_delegator]) return;

        uint256 index = delegatorIndex[_delegate][_delegator];
        uint256 lastIndex = delegators[_delegate].length - 1;

        if (index != lastIndex) {
            address lastDelegator = delegators[_delegate][lastIndex];
            delegators[_delegate][index] = lastDelegator;
            delegatorIndex[_delegate][lastDelegator] = index;
        }

        delegators[_delegate].pop();
        delete delegatorIndex[_delegate][_delegator];
        delete isDelegator[_delegate][_delegator];
    }
}
