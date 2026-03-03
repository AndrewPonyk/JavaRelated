import React, { useState, useEffect } from "react";
import { useWeb3 } from "../../hooks/useWeb3";
import { generateVoteProof, generateSecret, isZkAvailable } from "../../utils/zkProof";
import * as api from "../../utils/api";

function VotingBooth({ proposal, onVoteSubmitted }) {
  const { web3, account, isConnected } = useWeb3();

  const [selectedOption, setSelectedOption] = useState(null);
  const [secret, setSecret] = useState(null);
  const [commitHash, setCommitHash] = useState(null);
  const [phase, setPhase] = useState("select");
  const [error, setError] = useState(null);
  const [txHash, setTxHash] = useState(null);
  const [zkAvailable, setZkAvailable] = useState(false);
  const [votingAddress, setVotingAddress] = useState(null);
  const [votingAbi, setVotingAbi] = useState(null);
  const [mining, setMining] = useState(false);
  const [currentBlock, setCurrentBlock] = useState(null);

  useEffect(() => {
    isZkAvailable().then(setZkAvailable);
    api.getContractAddresses()
      .then((res) => {
        setVotingAddress(res.data.votingSystem);
        setVotingAbi(res.data.votingAbi);
      })
      .catch(() => {});
  }, []);

  const votingInstance = React.useMemo(() => {
    if (!web3 || !votingAbi || !votingAddress) return null;
    try {
      return new web3.eth.Contract(votingAbi, votingAddress);
    } catch {
      return null;
    }
  }, [web3, votingAbi, votingAddress]);

  async function handleCommit() {
    if (selectedOption === null || !isConnected || !web3) return;

    setPhase("committing");
    setError(null);

    try {
      const voteSecret = generateSecret();
      setSecret(voteSecret);

      const chainProposalId = Number(proposal.chain_proposal_id);
      if (!chainProposalId) {
        throw new Error("Proposal not yet created on-chain. Please advance it to voting first.");
      }

      // Pre-flight: verify the on-chain proposal exists and is in commit phase
      if (votingInstance) {
        try {
          const onChain = await votingInstance.methods.proposals(chainProposalId).call();
          const state = Number(onChain.state || onChain[6]); // state is 7th field
          if (state === 0) {
            throw new Error(
              "This proposal no longer exists on-chain (chain was reset). " +
              "Go back and re-advance the proposal to voting."
            );
          }
          if (state !== 1) { // 1 = CommitPhase
            const stateNames = ["Created", "Commit Phase", "Reveal Phase", "Tallied", "Cancelled"];
            throw new Error(`Proposal is in "${stateNames[state] || "unknown"}" phase, not Commit Phase.`);
          }
        } catch (preflight) {
          if (preflight.message.includes("on-chain") || preflight.message.includes("phase")) {
            throw preflight;
          }
          // If the call itself failed, the proposal likely doesn't exist
          throw new Error(
            "Could not verify on-chain proposal state. The chain may have been reset. " +
            "Go back and re-advance the proposal to voting."
          );
        }
      }

      // Generate commitment hash: keccak256(proposalId, choice, secret)
      const hash = web3.utils.soliditySha3(
        { type: "uint256", value: String(chainProposalId) },
        { type: "uint256", value: String(selectedOption) },
        { type: "bytes32", value: voteSecret }
      );
      setCommitHash(hash);

      if (votingInstance) {
        // Send on-chain transaction
        const tx = await votingInstance.methods
          .commitVote(chainProposalId, hash)
          .send({ from: account, gas: "3000000" });
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

  async function handleAdvanceToReveal() {
    setMining(true);
    setError(null);
    try {
      const chainProposalId = Number(proposal.chain_proposal_id);

      // Read actual on-chain deadline (DB value is an estimate that may be too low)
      let commitDeadline = Number(proposal.commit_deadline);
      if (votingInstance && chainProposalId) {
        const onChain = await votingInstance.methods.proposals(chainProposalId).call();
        commitDeadline = Number(onChain.commitDeadline || onChain[4]);
      }

      const curBlock = Number(await web3.eth.getBlockNumber());
      setCurrentBlock(curBlock);

      if (curBlock > commitDeadline) {
        setMining(false);
        return;
      }

      const blocksNeeded = commitDeadline - curBlock + 1;
      await api.mineBlocks(blocksNeeded);

      const newBlock = Number(await web3.eth.getBlockNumber());
      setCurrentBlock(newBlock);
    } catch (err) {
      setError(err.message || "Failed to advance blocks");
    } finally {
      setMining(false);
    }
  }

  async function handleReveal() {
    if (!secret || selectedOption === null || !isConnected) return;

    setPhase("revealing");
    setError(null);

    try {
      const chainProposalId = Number(proposal.chain_proposal_id);
      if (!chainProposalId) {
        throw new Error("Proposal not yet created on-chain. Cannot reveal.");
      }

      // Auto-advance past commit deadline if needed (Hardhat dev mode)
      // Read actual on-chain deadline (DB value is an estimate that may be too low)
      let commitDeadline = Number(proposal.commit_deadline || 0);
      if (votingInstance && chainProposalId) {
        const onChain = await votingInstance.methods.proposals(chainProposalId).call();
        commitDeadline = Number(onChain.commitDeadline || onChain[4]);
      }
      if (commitDeadline > 0) {
        const curBlock = Number(await web3.eth.getBlockNumber());
        if (curBlock <= commitDeadline) {
          await api.mineBlocks(commitDeadline - curBlock + 1);
        }
      }

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
            .send({ from: account, gas: "3000000" });
          setTxHash(tx.transactionHash);
        } else {
          // Use simplified reveal (no zk proof)
          const tx = await votingInstance.methods
            .revealVoteSimple(chainProposalId, selectedOption, secret)
            .send({ from: account, gas: "3000000" });
          setTxHash(tx.transactionHash);
        }
      }

      // Update DB phase to "reveal" (it's still "commit" in DB)
      try { await api.updatePhase(proposal.id, "reveal", null); } catch (_) { /* best-effort */ }

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

  async function handleTallyAndFinalize() {
    setMining(true);
    setError(null);
    try {
      const chainProposalId = Number(proposal.chain_proposal_id);

      // Mine past reveal deadline if needed
      // Read actual on-chain deadline (DB value is an estimate that may be too low)
      if (votingInstance) {
        const onChain = await votingInstance.methods.proposals(chainProposalId).call();
        const revealDeadline = Number(onChain.revealDeadline || onChain[5]);
        const curBlock = Number(await web3.eth.getBlockNumber());
        if (curBlock <= revealDeadline) {
          await api.mineBlocks(revealDeadline - curBlock + 1);
        }

        // Call tallyVotes on-chain
        await votingInstance.methods.tallyVotes(chainProposalId).send({ from: account, gas: "3000000" });
      }

      // Update DB phase
      try { await api.updatePhase(proposal.id, "tallied", null); } catch (_) { /* best-effort */ }

      setPhase("tallied");
      if (onVoteSubmitted) onVoteSubmitted();
    } catch (err) {
      const msg = err.message || "Failed to tally";
      if (msg.includes("revert")) {
        const match = msg.match(/reason string '(.+?)'/);
        setError(match ? match[1] : msg);
      } else {
        setError(msg);
      }
    } finally {
      setMining(false);
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

          {proposal.commit_deadline && (
            <div style={{ margin: "1rem 0", padding: "0.75rem", backgroundColor: "var(--bg-secondary)", borderRadius: "6px" }}>
              <p style={{ fontSize: "0.85rem", color: "var(--text-muted)" }}>
                Commit deadline: block #{proposal.commit_deadline}
                {currentBlock != null && <> | Current block: #{currentBlock}</>}
              </p>
              {(!currentBlock || currentBlock <= Number(proposal.commit_deadline)) && (
                <>
                  <p style={{ fontSize: "0.85rem", marginTop: "0.5rem" }}>
                    On Hardhat, blocks only advance with transactions. Click below to mine blocks and advance to the reveal phase.
                  </p>
                  <button onClick={handleAdvanceToReveal} disabled={mining} className="btn btn-primary" style={{ marginTop: "0.5rem" }}>
                    {mining ? "Mining blocks..." : "Advance to Reveal Phase"}
                  </button>
                </>
              )}
              {currentBlock != null && currentBlock > Number(proposal.commit_deadline) && (
                <p style={{ fontSize: "0.85rem", marginTop: "0.5rem", color: "var(--success)" }}>
                  Commit deadline passed. You can now reveal your vote.
                </p>
              )}
            </div>
          )}

          {error && <div className="error-message" style={{ marginTop: "0.5rem" }}>{error}</div>}

          <button onClick={handleReveal} className="btn btn-primary" style={{ marginTop: "0.5rem" }}>
            Reveal Vote Now
          </button>
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
          <p className="success-message">Vote revealed and counted on-chain!</p>
          {txHash && <p className="tx-hash">Tx: {txHash.slice(0, 10)}...{txHash.slice(-8)}</p>}
          <p style={{ margin: "1rem 0 0.5rem", color: "var(--text-muted)", fontSize: "0.85rem" }}>
            Finalize voting to see results. This will mine past the reveal deadline and tally votes on-chain.
          </p>
          {error && <div className="error-message">{error}</div>}
          <button onClick={handleTallyAndFinalize} disabled={mining} className="btn btn-primary">
            {mining ? "Finalizing..." : "Tally Votes & See Results"}
          </button>
        </div>
      )}

      {phase === "tallied" && (
        <div className="revealed-status">
          <p className="success-message">Voting finalized! Results are now visible.</p>
        </div>
      )}
    </div>
  );
}

export default VotingBooth;
