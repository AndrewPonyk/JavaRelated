#!/bin/bash
# Generate zkSNARK proving and verification keys from the circom circuit.
# This performs the "trusted setup" ceremony for development.
#
# Prerequisites:
#   npm install -g circom snarkjs
#
# For production, use a multi-party computation (MPC) ceremony instead.

set -e

CIRCUIT_DIR="contracts/zk"
BUILD_DIR="${CIRCUIT_DIR}/build"
CIRCUIT_NAME="vote"

echo "=== zkSNARK Key Generation ==="
echo "Circuit: ${CIRCUIT_DIR}/${CIRCUIT_NAME}.circom"

# Create build directory
mkdir -p "$BUILD_DIR"

# Step 1: Compile the circom circuit
echo "[1/5] Compiling circuit..."
circom "${CIRCUIT_DIR}/${CIRCUIT_NAME}.circom" \
  --r1cs --wasm --sym \
  -o "$BUILD_DIR"

# Step 2: Generate powers of tau (trusted setup phase 1)
echo "[2/5] Powers of tau ceremony..."
snarkjs powersoftau new bn128 14 "${BUILD_DIR}/pot14_0000.ptau" -v
snarkjs powersoftau contribute "${BUILD_DIR}/pot14_0000.ptau" "${BUILD_DIR}/pot14_0001.ptau" \
  --name="Development contribution" -v -e="random entropy for dev"
snarkjs powersoftau prepare phase2 "${BUILD_DIR}/pot14_0001.ptau" "${BUILD_DIR}/pot14_final.ptau" -v

# Step 3: Generate zkey (trusted setup phase 2)
echo "[3/5] Generating zkey..."
snarkjs groth16 setup "${BUILD_DIR}/${CIRCUIT_NAME}.r1cs" "${BUILD_DIR}/pot14_final.ptau" \
  "${BUILD_DIR}/${CIRCUIT_NAME}_0000.zkey"
snarkjs zkey contribute "${BUILD_DIR}/${CIRCUIT_NAME}_0000.zkey" "${BUILD_DIR}/${CIRCUIT_NAME}_final.zkey" \
  --name="Dev contribution" -v -e="more random entropy"

# Step 4: Export verification key
echo "[4/5] Exporting verification key..."
snarkjs zkey export verificationkey "${BUILD_DIR}/${CIRCUIT_NAME}_final.zkey" \
  "${BUILD_DIR}/${CIRCUIT_NAME}_verification_key.json"

# Step 5: Generate Solidity verifier contract
echo "[5/5] Generating Solidity verifier..."
snarkjs zkey export solidityverifier "${BUILD_DIR}/${CIRCUIT_NAME}_final.zkey" \
  "contracts/zkVerifier.sol"

echo ""
echo "=== Done! ==="
echo "Verification key: ${BUILD_DIR}/${CIRCUIT_NAME}_verification_key.json"
echo "Proving key:      ${BUILD_DIR}/${CIRCUIT_NAME}_final.zkey"
echo "WASM:             ${BUILD_DIR}/${CIRCUIT_NAME}_js/${CIRCUIT_NAME}.wasm"
echo "Verifier contract: contracts/zkVerifier.sol"
