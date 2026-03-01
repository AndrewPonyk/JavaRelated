import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useWeb3 } from "../../hooks/useWeb3";
import * as api from "../../utils/api";

function CreateProposal() {
  const { account, isConnected, authData, authenticate } = useWeb3();
  const navigate = useNavigate();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [options, setOptions] = useState(["Yes", "No"]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  function addOption() {
    if (options.length < 20) {
      setOptions([...options, ""]);
    }
  }

  function removeOption(index) {
    if (options.length > 2) {
      setOptions(options.filter((_, i) => i !== index));
    }
  }

  function updateOption(index, value) {
    const updated = [...options];
    updated[index] = value;
    setOptions(updated);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!isConnected) {
      setError("Please connect your wallet first.");
      return;
    }

    const validOptions = options.filter((o) => o.trim());
    if (validOptions.length < 2) {
      setError("Need at least 2 non-empty options.");
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      // Authenticate if not already
      let auth = authData;
      if (!auth) {
        auth = await authenticate();
      }
      if (!auth) {
        setError("Authentication required. Please sign the message in Metamask.");
        setSubmitting(false);
        return;
      }

      const result = await api.createProposal(
        { title, description, options: validOptions },
        auth
      );

      navigate(`/proposals/${result.data.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="create-proposal">
      <h2>Create New Proposal</h2>

      {error && <div className="error-message">{error}</div>}

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="title">Title</label>
          <input
            id="title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Proposal title"
            required
            minLength={3}
            maxLength={500}
          />
        </div>

        <div className="form-group">
          <label htmlFor="description">Description</label>
          <textarea
            id="description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe your proposal in detail..."
            required
            rows={6}
            minLength={10}
          />
        </div>

        <div className="form-group">
          <label>Voting Options (min 2, max 20)</label>
          {options.map((option, index) => (
            <div key={index} className="option-row">
              <input
                type="text"
                value={option}
                onChange={(e) => updateOption(index, e.target.value)}
                placeholder={`Option ${index + 1}`}
                required
              />
              {options.length > 2 && (
                <button type="button" onClick={() => removeOption(index)}>Remove</button>
              )}
            </div>
          ))}
          {options.length < 20 && (
            <button type="button" onClick={addOption} className="btn-add-option">
              + Add Option
            </button>
          )}
        </div>

        <button type="submit" disabled={submitting || !isConnected} className="btn btn-primary">
          {submitting ? "Creating..." : "Create Proposal"}
        </button>
      </form>
    </div>
  );
}

export default CreateProposal;
