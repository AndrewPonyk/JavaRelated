import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import * as api from "../../utils/api";
import { useWeb3 } from "../../hooks/useWeb3";
import VotingBooth from "../Voting/VotingBooth";
import ResultsDisplay from "../Voting/ResultsDisplay";
import LoadingSpinner from "../common/LoadingSpinner";

function ProposalDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { account, isConnected, authData, authenticate, web3, clearAuth } = useWeb3();

  const [proposal, setProposal] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [amendmentContent, setAmendmentContent] = useState("");
  const [submittingAmendment, setSubmittingAmendment] = useState(false);
  const [advancing, setAdvancing] = useState(false);
  const [tallying, setTallying] = useState(false);

  useEffect(() => {
    fetchProposal();
  }, [id]);

  async function fetchProposal() {
    setLoading(true);
    try {
      const result = await api.getProposal(id);
      setProposal(result.data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm("Delete this proposal?")) return;
    try {
      let auth = authData;
      if (!auth) auth = await authenticate();
      if (!auth) return;
      await api.deleteProposal(id, auth);
      navigate("/");
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleSubmitAmendment(e) {
    e.preventDefault();
    if (!amendmentContent.trim()) return;

    setSubmittingAmendment(true);
    try {
      let auth = authData;
      if (!auth) auth = await authenticate();
      if (!auth) return;
      await api.addAmendment(id, amendmentContent, auth);
      setAmendmentContent("");
      await fetchProposal();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmittingAmendment(false);
    }
  }

  async function handleAdvanceToVoting() {
    if (!isConnected) {
      setError("Please connect your wallet first.");
      return;
    }

    setAdvancing(true);
    setError(null);

    try {
      let auth = authData;

      // Try the API call
      try {
        var txData = await api.advanceToVoting(id, auth);
      } catch (apiErr) {
        // If nonce error, clear auth and retry
        if (apiErr.code === "NONCE_USED" || apiErr.code === "NONCE_EXPIRED" || apiErr.status === 401) {
          clearAuth();
          auth = await authenticate();
          if (!auth) {
            setError("Authentication required. Please sign the message in Metamask.");
            setAdvancing(false);
            return;
          }
          txData = await api.advanceToVoting(id, auth);
        } else {
          throw apiErr;
        }
      }

      const { contractAddress, abi, methodName, params, commitDeadline, revealDeadline } = txData.data;

      if (!web3) {
        throw new Error("Web3 not available. Please connect your wallet.");
      }

      // Verify contract has code before sending transaction
      const code = await web3.eth.getCode(contractAddress);
      if (!code || code === "0x" || code === "0x0") {
        throw new Error(
          "No contract deployed at " + contractAddress.slice(0, 10) + "... " +
          "The Hardhat node may have restarted. Restart the hardhat Docker container to redeploy."
        );
      }

      // Create contract instance and send transaction
      const contract = new web3.eth.Contract(abi, contractAddress);
      const tx = await contract.methods[methodName](...params).send({ from: account, gas: "3000000" });

      // Verify the transaction actually executed contract code (not just a bare transfer)
      const gasUsed = Number(tx.gasUsed || 0);
      if (gasUsed < 30000) {
        throw new Error(
          "Transaction used only " + gasUsed + " gas — contract code did not execute. " +
          "The contract may not be deployed. Restart the hardhat Docker container."
        );
      }

      // Extract the proposal ID from the transaction receipt (Web3.js v4 returns BigInt)
      let chainProposalId = null;
      if (tx.events && tx.events.ProposalCreated) {
        chainProposalId = Number(tx.events.ProposalCreated.returnValues?.proposalId);
      } else if (tx.logs && tx.logs.length > 0) {
        // Web3.js v4 may return logs instead of events
        const log = tx.logs.find(l => l.event === "ProposalCreated");
        chainProposalId = log ? Number(log.returnValues?.proposalId) : null;
      }

      if (!chainProposalId) {
        throw new Error("ProposalCreated event not found in transaction receipt. The contract call may have reverted.");
      }

      // Read actual on-chain deadlines (the backend estimate may differ from what the contract set)
      const onChainProposal = await contract.methods.proposals(chainProposalId).call();
      const actualCommitDeadline = Number(onChainProposal.commitDeadline || onChainProposal[4] || commitDeadline);
      const actualRevealDeadline = Number(onChainProposal.revealDeadline || onChainProposal[5] || revealDeadline);

      // Complete the advancement on the backend
      await api.completeAdvancement(id, Number(chainProposalId), actualCommitDeadline, actualRevealDeadline, auth);

      await fetchProposal();
    } catch (err) {
      setError(err.message || "Failed to advance proposal to voting");
    } finally {
      setAdvancing(false);
    }
  }

  async function handleTally() {
    if (!isConnected || !web3 || !proposal.chain_proposal_id) return;

    setTallying(true);
    setError(null);

    try {
      let auth = authData;
      if (!auth) auth = await authenticate();
      if (!auth) {
        setError("Authentication required.");
        setTallying(false);
        return;
      }

      const res = await api.getContractAddresses();
      const { votingSystem: contractAddress, votingAbi: abi } = res.data;
      if (!contractAddress || !abi) throw new Error("Contract not available");

      // PRE-MINE BLOCKS BEFORE WEB3 TRANSACTION
      // Auto-mine past reveal deadline for local dev
      // |su:5) Local Development Time Machine. To test phase transitions without waiting 10 hours for the Blockchain, we artificially advance the block height (mineBlocks api) before trying to Tally.
      // We do this via standard fetch/REST API first so Web3 doesn't time out
      const tempContract = new web3.eth.Contract(abi, contractAddress);
      const onChainProposal = await tempContract.methods.proposals(Number(proposal.chain_proposal_id)).call();
      const revealDeadline = Number(onChainProposal.revealDeadline || onChainProposal[5]);
      const curBlock = Number(await web3.eth.getBlockNumber());

      if (curBlock <= revealDeadline) {
        // Mine the required blocks via backend API
        await api.mineBlocks(revealDeadline - curBlock + 1);

        // Give Web3 a moment to recognize the new block height
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      // NOW we create the contract and send the transaction
      // Web3 will see the current block is already past the deadline
      const contract = new web3.eth.Contract(abi, contractAddress);
      await contract.methods.tallyVotes(Number(proposal.chain_proposal_id)).send({ from: account, gas: "3000000" });

      // Update phase to tallied in backend
      await api.updatePhase(proposal.id, "tallied", auth);

      await fetchProposal();
    } catch (err) {
      const msg = err.message || "Failed to tally votes";
      if (msg.includes("not mined within")) {
        console.warn("Ignoring Web3 timeout false-positive during local dev");
        try { await api.updatePhase(proposal.id, "tallied", authData); } catch (e) { console.error(e); }
        await fetchProposal();
        return;
      }
      if (msg.includes("revert")) {
        const match = msg.match(/reason string '(.+?)'/);
        setError(match ? match[1] : msg);
      } else {
        setError(msg);
      }
    } finally {
      setTallying(false);
    }
  }

  if (loading) return <LoadingSpinner />;
  if (error) return <div className="error-message">Error: {error}</div>;
  if (!proposal) return <div className="error-message">Proposal not found</div>;

  const isVotingActive = proposal.phase === "commit" || proposal.phase === "reveal";
  const isTallied = proposal.phase === "tallied";
  const isOwner = isConnected && account?.toLowerCase() === proposal.proposer_address?.toLowerCase();
  const canAmend = proposal.phase === "discussion" || proposal.phase === "draft";

  return (
    <div className="proposal-detail">
      <div className="page-header">
        <h1>{proposal.title}</h1>
        {isOwner && proposal.phase === "draft" && (
          <button onClick={handleDelete} className="btn btn-danger">Delete Draft</button>
        )}
      </div>

      <div className="proposal-info">
        <span className={`phase-badge phase-${proposal.phase}`}>{proposal.phase}</span>
        <span>By: {proposal.proposer_address?.slice(0, 10)}...</span>
        <span>Options: {proposal.option_count}</span>
        <span>Total votes: {proposal.total_votes || 0}</span>
        {proposal.chain_proposal_id && <span>Chain ID: #{proposal.chain_proposal_id}</span>}
      </div>

      <div className="proposal-body">
        <p>IPFS: {proposal.description_cid || "Not yet uploaded"}</p>
        {proposal.commit_deadline && <p>Commit deadline: block #{proposal.commit_deadline}</p>}
        {proposal.reveal_deadline && <p>Reveal deadline: block #{proposal.reveal_deadline}</p>}
      </div>

      {/* Options */}
      {proposal.options && proposal.options.length > 0 && (
        <div style={{ marginBottom: "1.5rem" }}>
          <h3>Options</h3>
          <ul className="proposals">
            {proposal.options.map((opt) => (
              <li key={opt.option_index} style={{ padding: "0.5rem 0", borderBottom: "1px solid var(--border)" }}>
                <strong>#{opt.option_index}</strong> — {opt.label}
                {isTallied && <span style={{ marginLeft: "1rem", color: "var(--primary)" }}>{opt.vote_count || 0} votes</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Amendments */}
      {proposal.amendments && proposal.amendments.length > 0 && (
        <div style={{ marginBottom: "1.5rem" }}>
          <h3>Amendments ({proposal.amendments.length})</h3>
          {proposal.amendments.map((a, i) => (
            <div key={i} className="proposal-card" style={{ padding: "0.75rem" }}>
              <span style={{ color: "var(--text-muted)", fontSize: "0.8rem" }}>
                By {a.author_address?.slice(0, 10)}... | {new Date(a.created_at).toLocaleDateString()}
              </span>
              <p style={{ fontSize: "0.85rem", marginTop: "0.25rem" }}>CID: {a.content_cid}</p>
            </div>
          ))}
        </div>
      )}

      {/* Add Amendment Form */}
      {canAmend && isConnected && (
        <form onSubmit={handleSubmitAmendment} style={{ marginBottom: "1.5rem" }}>
          <h3>Propose Amendment</h3>
          <div className="form-group">
            <textarea
              value={amendmentContent}
              onChange={(e) => setAmendmentContent(e.target.value)}
              placeholder="Describe your proposed amendment..."
              rows={3}
              required
              minLength={10}
            />
          </div>
          <button type="submit" disabled={submittingAmendment} className="btn btn-primary">
            {submittingAmendment ? "Submitting..." : "Submit Amendment"}
          </button>
        </form>
      )}

      {/* Advance to Voting Button - only for owner, draft phase */}
      {isOwner && proposal.phase === "draft" && (
        <div style={{ marginBottom: "1.5rem", padding: "1rem", backgroundColor: "var(--bg-secondary)", borderRadius: "8px" }}>
          <h3>Ready to Start Voting?</h3>
          <p style={{ marginBottom: "1rem", color: "var(--text-muted)" }}>
            Advance this proposal to the voting phase. Once advanced, the proposal will be created on-chain
            and voting will begin. This action cannot be undone.
          </p>
          <button onClick={handleAdvanceToVoting} disabled={advancing} className="btn btn-primary">
            {advancing ? "Advancing..." : "Advance to Voting"}
          </button>
        </div>
      )}

      {/* Stale chain state warning — proposal says voting but no chain ID */}
      {isVotingActive && !proposal.chain_proposal_id && (
        <div style={{ marginBottom: "1.5rem", padding: "1rem", backgroundColor: "#fff3cd", borderRadius: "8px", border: "1px solid #ffc107" }}>
          <h3 style={{ color: "#856404" }}>Chain Data Stale</h3>
          <p style={{ color: "#856404" }}>
            This proposal was in voting phase but the blockchain was reset.
            The proposal needs to be re-advanced to voting.
          </p>
          {isOwner && (
            <button onClick={handleAdvanceToVoting} disabled={advancing} className="btn btn-primary">
              {advancing ? "Re-advancing..." : "Re-advance to Voting"}
            </button>
          )}
        </div>
      )}

      {isVotingActive && proposal.chain_proposal_id && (
        <VotingBooth proposal={proposal} onVoteSubmitted={fetchProposal} />
      )}

      {/* Tally button — available after reveal phase ends */}
      {proposal.phase === "reveal" && isConnected && proposal.chain_proposal_id && (
        <div style={{ marginBottom: "1.5rem", padding: "1rem", backgroundColor: "var(--bg-secondary)", borderRadius: "8px" }}>
          <h3>Finalize Results</h3>
          <p style={{ marginBottom: "1rem", color: "var(--text-muted)" }}>
            Click to tally the votes on-chain and finalize the results.
          </p>
          <button onClick={handleTally} disabled={tallying} className="btn btn-primary">
            {tallying ? "Tallying..." : "Tally Votes"}
          </button>
        </div>
      )}

      {isTallied && <ResultsDisplay proposalId={proposal.id} />}
    </div>
  );
}

export default ProposalDetail;
