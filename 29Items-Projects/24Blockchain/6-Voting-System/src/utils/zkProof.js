/**
 * Client-side zkSNARK proof generation for anonymous voting.
 *
 * Uses snarkjs to generate Groth16 proofs from the compiled circom circuit.
 * The proving key and WASM must be available at the configured URLs.
 * If the circuit is not yet compiled (development), returns a placeholder proof
 * that signals to the contract that zk verification should be skipped.
 */

const PROVING_KEY_URL = "/zk/vote_final.zkey";
const WASM_URL = "/zk/vote.wasm";
const VERIFICATION_KEY_URL = "/zk/vote_verification_key.json";

let snarkjs = null;
let provingKeyAvailable = null; // null = not checked, true/false = cached result

/**
 * Lazily load snarkjs. Returns null if not available.
 */
async function loadSnarkJs() {
  if (snarkjs !== null) return snarkjs;
  try {
    snarkjs = await import("snarkjs");
    return snarkjs;
  } catch {
    snarkjs = false;
    return null;
  }
}

/**
 * Check if the proving key is available for download.
 */
async function checkProvingKey() {
  if (provingKeyAvailable !== null) return provingKeyAvailable;
  try {
    const res = await fetch(PROVING_KEY_URL, { method: "HEAD" });
    provingKeyAvailable = res.ok;
  } catch {
    provingKeyAvailable = false;
  }
  return provingKeyAvailable;
}

/**
 * Generate a zkSNARK proof for a vote.
 *
 * @param {object} params
 * @param {number} params.choice - The vote choice index
 * @param {string} params.secret - Random secret (hex string)
 * @param {string} params.voterKey - Voter's private identifier
 * @param {number} params.proposalId - The proposal being voted on
 * @returns {Promise<{ proof, publicSignals, isReal }>}
 */
export async function generateVoteProof({ choice, secret, voterKey, proposalId }) {
  const snarks = await loadSnarkJs();
  const keyAvailable = await checkProvingKey();

  if (snarks && keyAvailable) {
    // Real proof generation with snarkjs
    const input = {
      choice: BigInt(choice),
      secret: BigInt(secret),
      voterKey: BigInt(voterKey),
      commitment: 0n, // computed inside the circuit
      nullifier: 0n,  // computed inside the circuit
      proposalId: BigInt(proposalId),
    };

    const { proof, publicSignals } = await snarks.groth16.fullProve(
      input,
      WASM_URL,
      PROVING_KEY_URL
    );

    return {
      proof,
      publicSignals,
      isReal: true,
      // Format for Solidity contract
      solidityProof: {
        a: [proof.pi_a[0], proof.pi_a[1]],
        b: [
          [proof.pi_b[0][1], proof.pi_b[0][0]],
          [proof.pi_b[1][1], proof.pi_b[1][0]],
        ],
        c: [proof.pi_c[0], proof.pi_c[1]],
        input: publicSignals.map(String),
      },
    };
  }

  // Development fallback: return empty proof structure
  // The contract's revealVoteSimple() function is used when zk is disabled
  return {
    proof: { pi_a: ["0", "0", "1"], pi_b: [["0", "0"], ["0", "0"], ["1", "0"]], pi_c: ["0", "0", "1"] },
    publicSignals: ["0", "0", String(proposalId)],
    isReal: false,
    solidityProof: {
      a: ["0", "0"],
      b: [["0", "0"], ["0", "0"]],
      c: ["0", "0"],
      input: ["0", "0", String(proposalId)],
    },
  };
}

/**
 * Verify a zkSNARK proof locally (for debugging / client-side validation).
 */
export async function verifyVoteProof(proof, publicSignals) {
  const snarks = await loadSnarkJs();
  if (!snarks) return false;

  try {
    const vkeyResponse = await fetch(VERIFICATION_KEY_URL);
    if (!vkeyResponse.ok) return false;
    const vkey = await vkeyResponse.json();
    return await snarks.groth16.verify(vkey, publicSignals, proof);
  } catch {
    return false;
  }
}

/**
 * Generate a random secret for vote commitment.
 * @returns {string} Random 32-byte hex string
 */
export function generateSecret() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return "0x" + Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Check if zk proof generation is available.
 */
export async function isZkAvailable() {
  const snarks = await loadSnarkJs();
  const keyAvailable = await checkProvingKey();
  return !!(snarks && keyAvailable);
}

export default { generateVoteProof, verifyVoteProof, generateSecret, isZkAvailable };
