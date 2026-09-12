import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";

import { api } from "../api/client";
import { AsyncBlock } from "../components/AsyncBlock";
import { usePortfolios } from "../hooks/usePortfolio";

export function PortfoliosPage() {
  const { data, loading, error } = usePortfolios();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (!name.trim()) {
      setFormError("Name is required.");
      return;
    }
    setBusy(true);
    try {
      const p = await api.createPortfolio({ name, description: description || undefined, holdings: [] });
      navigate(`/portfolios/${p.id}`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to create portfolio");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1>Your portfolios</h1>
      <AsyncBlock loading={loading} error={error}>
        <ul className="list">
          {data?.map((p) => (
            <li key={p.id}>
              <Link to={`/portfolios/${p.id}`}>{p.name}</Link>{" "}
              <span className="muted">({p.holdings.length} holdings)</span>
            </li>
          ))}
          {data && data.length === 0 && <li className="muted">No portfolios yet — create one below.</li>}
        </ul>
      </AsyncBlock>

      <section className="panel">
        <h2>Create portfolio</h2>
        <form className="inline-form" onSubmit={create}>
          <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <input
            placeholder="Description (optional)"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
          <button type="submit" disabled={busy}>
            {busy ? "Creating…" : "Create"}
          </button>
        </form>
        {formError && <p className="error">{formError}</p>}
      </section>
    </div>
  );
}
