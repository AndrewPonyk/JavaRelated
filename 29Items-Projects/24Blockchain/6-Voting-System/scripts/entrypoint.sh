#!/bin/sh
# Start Hardhat node in background, deploy contracts, keep running
set -e

# Remove stale deployment addresses from previous runs
# (the chain state is ephemeral but this file persists via volume mount)
rm -f /app/ignition/deployments/chain-1337/deployed_addresses.json

echo "Starting Hardhat node..."
npx hardhat node --hostname 0.0.0.0 &
HARDHAT_PID=$!

# Wait for the node to be ready
echo "Waiting for Hardhat node to be ready..."
for i in $(seq 1 30); do
  if curl -sf -X POST http://127.0.0.1:8545 \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' > /dev/null 2>&1; then
    echo "Hardhat node is ready!"
    break
  fi
  if [ "$i" = "30" ]; then
    echo "ERROR: Hardhat node failed to start after 30 seconds"
    exit 1
  fi
  sleep 1
done

# Compile and deploy contracts
echo "Compiling contracts..."
npx hardhat compile --force || { echo "ERROR: Contract compilation failed"; exit 1; }

echo "Deploying contracts..."
npx hardhat run scripts/deploy.js --network localhost || { echo "ERROR: Contract deployment failed"; exit 1; }

# Verify deployment succeeded
if [ ! -f /app/ignition/deployments/chain-1337/deployed_addresses.json ]; then
  echo "ERROR: deployed_addresses.json not created after deployment"
  exit 1
fi

echo "Contracts deployed and verified!"

# Verify contracts have code at deployed addresses
echo "Verifying contract code on-chain..."
VOTING_ADDR=$(node -e "const a=require('./ignition/deployments/chain-1337/deployed_addresses.json'); console.log(a['VotingSystemModule#VotingSystem'])")
CODE_RESULT=$(curl -sf -X POST http://127.0.0.1:8545 \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getCode\",\"params\":[\"$VOTING_ADDR\",\"latest\"],\"id\":1}")
CODE_LENGTH=$(echo "$CODE_RESULT" | node -e "process.stdin.on('data',d=>{const r=JSON.parse(d);console.log(r.result?r.result.length:0)})")

if [ "$CODE_LENGTH" -le 2 ]; then
  echo "ERROR: No contract code at VotingSystem address $VOTING_ADDR"
  exit 1
fi
echo "VotingSystem verified at $VOTING_ADDR (code length: $CODE_LENGTH)"

# Keep the node running
wait $HARDHAT_PID
