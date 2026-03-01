#!/bin/bash
# Run MythX automated security audit on all smart contracts.
#
# Prerequisites:
#   npm install -g @mythx/cli
#   export MYTHX_API_KEY=your_api_key
#
# MythX detects: reentrancy, integer overflow, access control, tx-origin,
# uninitialized storage, and other Solidity vulnerabilities.

set -e

echo "=== MythX Smart Contract Security Audit ==="

if [ -z "$MYTHX_API_KEY" ]; then
  echo "ERROR: MYTHX_API_KEY not set. Get a key at https://mythx.io"
  exit 1
fi

# Compile contracts first
echo "[1/3] Compiling contracts..."
npx hardhat compile

# Run MythX analysis on each contract
echo "[2/3] Submitting to MythX for analysis..."

CONTRACTS=(
  "contracts/VotingSystem.sol"
  "contracts/ProposalManager.sol"
  "contracts/DelegationRegistry.sol"
)

for contract in "${CONTRACTS[@]}"; do
  echo "  Analyzing: $contract"
  mythx analyze "$contract" \
    --mode deep \
    --solc-version 0.8.19 \
    --remap "@openzeppelin/=$(pwd)/node_modules/@openzeppelin/" \
    || echo "  WARNING: Analysis failed for $contract"
done

echo "[3/3] Analysis submitted. Check https://dashboard.mythx.io for results."
echo ""
echo "=== Done ==="
