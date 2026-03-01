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
  const { account, isConnected, authData, authenticate } = useWeb3();

  const [proposal, setProposal] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [amendmentContent, setAmendmentContent] = useState("");
  const [submittingAmendment, setSubmittingAmendment] = useState(false);

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

      {isVotingActive && <VotingBooth proposal={proposal} onVoteSubmitted={fetchProposal} />}
      {isTallied && <ResultsDisplay proposalId={proposal.id} />}
      {!isVotingActive && !isTallied && proposal.phase !== "cancelled" && (
        <ResultsDisplay proposalId={proposal.id} />
      )}
    </div>
  );
}

export default ProposalDetail;
