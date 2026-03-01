import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import * as api from "../../utils/api";
import { PROPOSAL_PHASES } from "../../utils/constants";
import LoadingSpinner from "../common/LoadingSpinner";

function ProposalList() {
  const [proposals, setProposals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filterPhase, setFilterPhase] = useState("");
  const [pagination, setPagination] = useState({ page: 1, total: 0 });

  useEffect(() => {
    fetchProposals();
  }, [filterPhase]);

  async function fetchProposals() {
    setLoading(true);
    setError(null);

    try {
      const result = await api.listProposals({
        phase: filterPhase || undefined,
        page: pagination.page,
        limit: 20,
      });
      setProposals(result.data);
      if (result.pagination) {
        setPagination(result.pagination);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <LoadingSpinner />;
  if (error) return <div className="error-message">Error: {error}</div>;

  return (
    <div className="proposal-list">
      <div className="list-header">
        <h2>Proposals ({pagination.total || proposals.length})</h2>
        <select value={filterPhase} onChange={(e) => setFilterPhase(e.target.value)}>
          <option value="">All Phases</option>
          {Object.entries(PROPOSAL_PHASES).map(([key, value]) => (
            <option key={key} value={value}>
              {key}
            </option>
          ))}
        </select>
      </div>

      {proposals.length === 0 ? (
        <p className="empty-state">No proposals found. Create one to get started.</p>
      ) : (
        <ul className="proposals">
          {proposals.map((proposal) => (
            <li key={proposal.id} className="proposal-card">
              <Link to={`/proposals/${proposal.id}`}>
                <h3>{proposal.title}</h3>
                <div className="proposal-meta">
                  <span className={`phase-badge phase-${proposal.phase}`}>{proposal.phase}</span>
                  <span>Options: {proposal.option_count}</span>
                  <span>Votes: {proposal.total_votes || 0}</span>
                  <span>By: {proposal.proposer_address?.slice(0, 8)}...</span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default ProposalList;
