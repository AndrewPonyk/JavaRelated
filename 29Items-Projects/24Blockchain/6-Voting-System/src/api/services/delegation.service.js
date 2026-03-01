const db = require("../utils/db");
const blockchainService = require("./blockchain.service");
const logger = require("../utils/logger");

/**
 * Get delegation info for an address.
 * Returns who they've delegated to and who has delegated to them.
 */
async function getDelegationInfo(address) {
  const normalized = address.toLowerCase();

  // Who this address has currently delegated to (most recent active delegation)
  const delegatedTo = await db.query(
    `SELECT DISTINCT ON (from_address)
       to_address, proposal_id, created_at
     FROM delegation_events
     WHERE from_address = $1 AND event_type = 'delegated'
       AND NOT EXISTS (
         SELECT 1 FROM delegation_events de2
         WHERE de2.from_address = $1
           AND de2.event_type = 'revoked'
           AND de2.created_at > delegation_events.created_at
           AND de2.proposal_id = delegation_events.proposal_id
       )
     ORDER BY from_address, created_at DESC`,
    [normalized]
  );

  // Who has delegated to this address (active delegations only)
  const delegatedFrom = await db.query(
    `SELECT DISTINCT ON (from_address)
       from_address, proposal_id, created_at
     FROM delegation_events
     WHERE to_address = $1 AND event_type = 'delegated'
       AND NOT EXISTS (
         SELECT 1 FROM delegation_events de2
         WHERE de2.from_address = delegation_events.from_address
           AND de2.event_type = 'revoked'
           AND de2.created_at > delegation_events.created_at
           AND de2.proposal_id = delegation_events.proposal_id
       )
     ORDER BY from_address, created_at DESC`,
    [normalized]
  );

  return {
    address: normalized,
    delegatedTo: delegatedTo.rows[0] || null,
    receivedDelegations: delegatedFrom.rows,
  };
}

/**
 * Get effective voting power for an address.
 * Queries on-chain contract first, falls back to off-chain cache.
 */
async function getVotingPower(address) {
  const normalized = address.toLowerCase();

  // Try on-chain first
  const onChainPower = await blockchainService.getOnChainVotingPower(normalized);
  if (onChainPower > 0) {
    return onChainPower;
  }

  // Fallback to off-chain calculation
  const info = await getDelegationInfo(normalized);
  return 1 + info.receivedDelegations.length;
}

/**
 * Record a delegation event from the blockchain.
 */
async function handleDelegationEvent({ fromAddress, toAddress, proposalId, eventType, txHash, blockNumber }) {
  await db.query(
    `INSERT INTO delegation_events (from_address, to_address, proposal_id, event_type, tx_hash, block_number)
     VALUES ($1, $2, $3, $4, $5, $6)`,
    [fromAddress.toLowerCase(), toAddress.toLowerCase(), proposalId, eventType, txHash, blockNumber]
  );
  logger.info(`Delegation ${eventType}: ${fromAddress} -> ${toAddress} for proposal ${proposalId}`);
}

/**
 * Get the delegation history for an address.
 */
async function getDelegationHistory(address, limit = 50) {
  const normalized = address.toLowerCase();
  const result = await db.query(
    `SELECT * FROM delegation_events
     WHERE from_address = $1 OR to_address = $1
     ORDER BY created_at DESC LIMIT $2`,
    [normalized, limit]
  );
  return result.rows;
}

module.exports = { getDelegationInfo, getVotingPower, handleDelegationEvent, getDelegationHistory };
