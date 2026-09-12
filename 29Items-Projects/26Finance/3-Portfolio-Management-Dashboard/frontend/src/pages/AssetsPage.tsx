import { useState } from "react";
import type { FormEvent } from "react";

import { api } from "../api/client";
import { AsyncBlock } from "../components/AsyncBlock";
import { useAsync } from "../hooks/usePortfolio";

export function AssetsPage() {
  const assets = useAsync(() => api.listAssets(), []);
  const [symbol, setSymbol] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!symbol.trim() || !name.trim()) {
      setError("Symbol and name are required.");
      return;
    }
    setBusy(true);
    try {
      await api.createAsset({ symbol: symbol.toUpperCase(), name });
      setSymbol("");
      setName("");
      assets.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create asset");
    } finally {
      setBusy(false);
    }
  };

  const seed = async (assetId: number) => {
    await api.seedPrices(assetId, 504);
    assets.reload();
  };

  return (
    <div>
      <h1>Assets</h1>
      <p className="muted">
        Create assets and seed synthetic price history so portfolios that hold them can be analyzed.
      </p>

      <AsyncBlock loading={assets.loading} error={assets.error}>
        <table className="table">
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Name</th>
              <th>Class</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {assets.data?.map((a) => (
              <tr key={a.id}>
                <td>{a.symbol}</td>
                <td>{a.name}</td>
                <td>{a.asset_class}</td>
                <td>
                  <button onClick={() => seed(a.id)}>Seed prices</button>
                </td>
              </tr>
            ))}
            {assets.data && assets.data.length === 0 && (
              <tr>
                <td colSpan={4} className="muted">
                  No assets yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </AsyncBlock>

      <section className="panel">
        <h2>New asset</h2>
        <form className="inline-form" onSubmit={create}>
          <input placeholder="Symbol (e.g. AAPL)" value={symbol} onChange={(e) => setSymbol(e.target.value)} />
          <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <button type="submit" disabled={busy}>
            {busy ? "Creating…" : "Create asset"}
          </button>
        </form>
        {error && <p className="error">{error}</p>}
      </section>
    </div>
  );
}
