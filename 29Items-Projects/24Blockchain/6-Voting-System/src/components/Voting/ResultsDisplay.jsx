import React, { useState, useEffect } from "react";
import * as api from "../../utils/api";
import LoadingSpinner from "../common/LoadingSpinner";

function ResultsDisplay({ proposalId }) {
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchResults();
  }, [proposalId]);

  async function fetchResults() {
    try {
      const { data } = await api.getResults(proposalId);
      setResults(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <LoadingSpinner message="Loading results..." />;
  if (error) return <div className="error-message">{error}</div>;
  if (!results || !results.options || results.options.length === 0) return null;

  const maxVotes = Math.max(...results.options.map((o) => o.votes), 1);

  return (
    <div className="results-display">
      <h3>Voting Results</h3>
      <p style={{ color: "var(--text-muted)", marginBottom: "1rem" }}>
        Total votes: {results.totalVotes} | Phase: {results.phase}
      </p>

      <div className="results-bars">
        {results.options.map((option) => (
          <div key={option.index} className="result-row">
            <div className="result-label">
              <span>{option.label}</span>
              <span>
                {option.votes} ({option.percentage}%)
              </span>
            </div>
            <div className="result-bar-bg">
              <div
                className="result-bar-fill"
                style={{ width: `${results.totalVotes > 0 ? (option.votes / maxVotes) * 100 : 0}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default ResultsDisplay;
