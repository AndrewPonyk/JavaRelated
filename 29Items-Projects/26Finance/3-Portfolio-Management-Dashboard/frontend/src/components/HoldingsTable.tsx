// Holdings table with inline add/remove. Calls the API then asks the parent to
// reload the portfolio so derived analytics stay consistent.
import { useState } from "react";
import type { FormEvent } from "react";

import { api } from "../api/client";
import type { Asset, Holding } from "../types/portfolio";

interface Props {
  portfolioId: number;
  holdings: Holding[];
  assets: Asset[];
  onChange: () => void;
}

export function HoldingsTable({ portfolioId, holdings, assets, onChange }: Props) {
  const [assetId, setAssetId] = useState<number | "">("");
  const [quantity, setQuantity] = useState("");
  const [costBasis, setCostBasis] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const symbolFor = (id: number) => assets.find((a) => a.id === id)?.symbol ?? `#${id}`;

  const add = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (assetId === "" || !quantity || Number(quantity) <= 0) {
      setError("Pick an asset and a positive quantity.");
      return;
    }
    setBusy(true);
    try {
      await api.addHolding(portfolioId, {
        asset_id: Number(assetId),
        quantity: Number(quantity),
        cost_basis: costBasis ? Number(costBasis) : 0,
      });
      setAssetId("");
      setQuantity("");
      setCostBasis("");
      onChange();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add holding");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: number) => {
    await api.deleteHolding(portfolioId, id);
    onChange();
  };

  return (
    <div>
      <table className="table">
        <thead>
          <tr>
            <th>Asset</th>
            <th>Quantity</th>
            <th>Cost basis</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {holdings.map((h) => (
            <tr key={h.id}>
              <td>{h.asset?.symbol ?? symbolFor(h.asset_id)}</td>
              <td>{h.quantity}</td>
              <td>{h.cost_basis}</td>
              <td>
                <button className="link-danger" onClick={() => remove(h.id)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
          {holdings.length === 0 && (
            <tr>
              <td colSpan={4} className="muted">
                No holdings yet.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      <form className="inline-form" onSubmit={add}>
        <select value={assetId} onChange={(e) => setAssetId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Select asset…</option>
          {assets.map((a) => (
            <option key={a.id} value={a.id}>
              {a.symbol} — {a.name}
            </option>
          ))}
        </select>
        <input
          type="number"
          step="any"
          min="0"
          placeholder="Quantity"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
        />
        <input
          type="number"
          step="any"
          min="0"
          placeholder="Cost basis"
          value={costBasis}
          onChange={(e) => setCostBasis(e.target.value)}
        />
        <button type="submit" disabled={busy}>
          {busy ? "Adding…" : "Add holding"}
        </button>
      </form>
      {error && <p className="error">{error}</p>}
    </div>
  );
}
