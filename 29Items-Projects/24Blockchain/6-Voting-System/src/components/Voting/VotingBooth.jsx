import React, { useState, useEffect } from "react";
import { useWeb3 } from "../../hooks/useWeb3";
import useContract from "../../hooks/useContract";
import { generateVoteProof, generateSecret, isZkAvailable } from "../../utils/zkProof";
import { CONTRACT_ADDRESSES } from "../../utils/constants";

function VotingBooth({ proposal, onVoteSubmitted }) {
  const { web3, account, isConnected } = useWeb3();
  const { contract: votingContract } = useContract(
    null, // ABI loaded dynamically
    CONTRACT_ADDRESSES.votingSystem
  );

  const [selectedOption, setSelectedOption] = useState(null);
  const [secret, setSecret] = useState(null);
  const [commitHash, setCommitHash] = useState(null);
  const [phase, setPhase] = useState("select");
  const [error, setError] = useState(null);
  const [txHash, setTxHash] = useState(null);
  const [zkAvailable, setZkAvailable] = useState(false);

  useEffect(() => {
    isZkAvailable().then(setZkAvailable);
  }, []);

  // Load VotingSystem ABI dynamically (Hardhat artifact path, with Truffle fallback)
  const [votingAbi, setVotingAbi] = useState(null);
  useEffect(() => {
    fetch("/artifacts/contracts/VotingSystem.sol/VotingSystem.json")
      .then((r) => r.ok ? r.json() : fetch("/contracts/VotingSystem.json"))
      .then((r) => r.json ? r.json() : r)
      .then(setVotingAbi)
      .catch(() => setVotingAbi(null));
  }, []);

  const votingInstance = React.useMemo(() => {
    if (!web3 || !votingAbi || !CONTRACT_ADDRESSES.votingSystem) return null;
    try {
      return new web3.eth.Contract(votingAbi.abi, CONTRACT_ADDRESSES.votingSystem);
    } catch {
      return null;
    }
  }, [web3, votingAbi]);

  async function handleCommit() {
    if (selectedOption === null || !isConnected || !web3) return;

    setPhase("committing");
    setError(null);

    try {
      const voteSecret = generateSecret();
      setSecret(voteSecret);

      const chainProposalId = proposal.chain_proposal_id || proposal.id;

      // Generate commitment hash: keccak256(proposalId, choice, secret)
      const hash = web3.utils.soliditySha3(
        { type: "uint256", value: chainProposalId },
        { type: "uint256", value: selectedOption },
        { type: "bytes32", value: voteSecret }
      );
      setCommitHash(hash);

      if (votingInstance) {
        // Send on-chain transaction
        const tx = await votingInstance.methods
          .commitVote(chainProposalId, hash)
          .send({ from: account });
        setTxHash(tx.transactionHash);
      }

      setPhase("committed");
    } catch (err) {
      const msg = err.message || "Failed to commit vote";
      // Parse contract revert reason if available
      if (msg.includes("revert")) {
        const match = msg.match(/reason string '(.+?)'/);
        setError(match ? match[1] : msg);
      } else {
        setError(msg);
      }
      setPhase("select");
    }
  }

  async function handleReveal() {
    if (!secret || selectedOption === null || !isConnected) return;

    setPhase("revealing");
    setError(null);

    try {
      const chainProposalId = proposal.chain_proposal_id || proposal.id;

      if (votingInstance) {
        if (zkAvailable) {
          // Generate zkSNARK proof and use the full revealVote
          const { solidityProof } = await generateVoteProof({
            choice: selectedOption,
            secret,
            voterKey: account,
            proposalId: chainProposalId,
          });

          const tx = await votingInstance.methods
            .revealVote(
              chainProposalId,
              selectedOption,
              secret,
              solidityProof.a,
              solidityProof.b,
              solidityProof.c,
              solidityProof.input
            )
            .send({ from: account });
          setTxHash(tx.transactionHash);
        } else {
          // Use simplified reveal (no zk proof)
          const tx = await votingInstance.methods
            .revealVoteSimple(chainProposalId, selectedOption, secret)
            .send({ from: account });
          setTxHash(tx.transactionHash);
        }
      }

      setPhase("revealed");
      if (onVoteSubmitted) onVoteSubmitted();
    } catch (err) {
      const msg = err.message || "Failed to reveal vote";
      if (msg.includes("revert")) {
        const match = msg.match(/reason string '(.+?)'/);
        setError(match ? match[1] : msg);
      } else {
        setError(msg);
      }
      setPhase("committed");
    }
  }

  if (!isConnected) {
    return <div className="voting-booth"><p className="info-message">Connect your wallet to vote.</p></div>;
  }

  return (
    <div className="voting-booth">
      <h3>Cast Your Vote</h3>
      {!zkAvailable && <p className="info-message zk-notice">zkSNARK proofs not available. Using simplified voting mode.</p>}

      {error && <div className="error-message">{error}</div>}

      {phase === "select" && (
        <div className="vote-options">
          {(proposal.options || []).map((option) => (
            <label key={option.option_index} className={`vote-option ${selectedOption === option.option_index ? "selected" : ""}`}>
              <input
                type="radio"
                name="vote-choice"
                value={option.option_index}
                checked={selectedOption === option.option_index}
                onChange={() => setSelectedOption(option.option_index)}
              />
              <span className="option-label">{option.label}</span>
            </label>
          ))}
          <button onClick={handleCommit} disabled={selectedOption === null} className="btn btn-primary">
            Commit Vote
          </button>
          <p className="vote-help">Your vote will be hidden until the reveal phase.</p>
        </div>
      )}

      {phase === "committing" && (
        <div className="voting-status">
          <div className="spinner" />
          <p>Submitting commitment to blockchain...</p>
          <p className="vote-help">Please confirm the transaction in Metamask.</p>
        </div>
      )}

      {phase === "committed" && (
        <div className="committed-status">
          <p className="success-message">Vote committed successfully!</p>
          {txHash && <p className="tx-hash">Tx: {txHash.slice(0, 10)}...{txHash.slice(-8)}</p>}
          <div className="secret-section">
            <p><strong>Save your secret — you need it for the reveal phase:</strong></p>
            <code className="secret-display">{secret}</code>
            <button onClick={() => navigator.clipboard?.writeText(secret)} className="btn btn-small">Copy Secret</button>
          </div>
          {(proposal.phase === "reveal" || proposal.phase === "commit") && (
            <button onClick={handleReveal} className="btn btn-primary">
              Reveal Vote Now
            </button>
          )}
        </div>
      )}

      {phase === "revealing" && (
        <div className="voting-status">
          <div className="spinner" />
          <p>{zkAvailable ? "Generating zero-knowledge proof and revealing..." : "Revealing vote..."}</p>
          <p className="vote-help">Please confirm the transaction in Metamask.</p>
        </div>
      )}

      {phase === "revealed" && (
        <div className="revealed-status">
          <p className="success-message">Vote revealed and counted!</p>
          {txHash && <p className="tx-hash">Tx: {txHash.slice(0, 10)}...{txHash.slice(-8)}</p>}
        </div>
      )}
    </div>
  );
}

export default VotingBooth;
