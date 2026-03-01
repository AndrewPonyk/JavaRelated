// zkSNARK circuit for anonymous vote verification
// Proves that a voter knows a valid vote choice and secret
// without revealing the choice itself.
//
// TODO: This is a simplified stub circuit. Production circuit needs:
// - Merkle tree membership proof (voter is in eligible voter set)
// - Nullifier to prevent double voting
// - Range check on vote choice

pragma circom 2.0.0;

include "circomlib/circuits/poseidon.circom";
include "circomlib/circuits/comparators.circom";

template VoteProof(nOptions) {
    // Private inputs (known only to the voter)
    signal input choice;          // The vote choice (0 to nOptions-1)
    signal input secret;          // Random secret for commitment
    signal input voterKey;        // Voter's private key / identifier

    // Public inputs (visible to verifier)
    signal input commitment;      // hash(choice, secret) — must match on-chain commitment
    signal input nullifier;       // hash(voterKey, proposalId) — prevents double voting
    signal input proposalId;      // The proposal being voted on

    // Output
    signal output valid;

    // 1. Verify commitment = Poseidon(choice, secret)
    component commitHash = Poseidon(2);
    commitHash.inputs[0] <== choice;
    commitHash.inputs[1] <== secret;
    commitment === commitHash.out;

    // 2. Verify nullifier = Poseidon(voterKey, proposalId)
    component nullHash = Poseidon(2);
    nullHash.inputs[0] <== voterKey;
    nullHash.inputs[1] <== proposalId;
    nullifier === nullHash.out;

    // 3. Range check: 0 <= choice < nOptions
    component lt = LessThan(8);  // 8 bits supports up to 256 options
    lt.in[0] <== choice;
    lt.in[1] <== nOptions;
    lt.out === 1;

    valid <== 1;
}

// Instantiate with max 10 voting options
component main {public [commitment, nullifier, proposalId]} = VoteProof(10);
