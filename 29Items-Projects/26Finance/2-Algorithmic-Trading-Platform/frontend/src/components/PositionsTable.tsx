// Live positions table with loading / error / empty states.

import type { Position } from '../types/strategy';

function num(value: string): string {
  return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function PnLCell({ value }: { value: string }) {
  const n = Number(value);
  return <td className={`num ${n > 0 ? 'pos' : n < 0 ? 'neg' : ''}`}>{num(value)}</td>;
}

interface Props {
  positions: Position[];
  loading: boolean;
  error: string | null;
}

export default function PositionsTable({ positions, loading, error }: Props) {
  if (loading && positions.length === 0) return <div className="card">Loading positions…</div>;
  if (error) return <div className="card error">Could not load positions: {error}</div>;
  if (positions.length === 0) return <div className="card muted">No open positions.</div>;

  return (
    <div className="card">
      <h3>Positions</h3>
      <table>
        <thead>
          <tr>
            <th>Symbol</th>
            <th className="num">Qty</th>
            <th className="num">Avg price</th>
            <th className="num">Realized</th>
            <th className="num">Unrealized</th>
          </tr>
        </thead>
        <tbody>
          {positions.map((p) => (
            <tr key={p.symbol}>
              <td>{p.symbol}</td>
              <td className="num">{p.quantity.toLocaleString()}</td>
              <td className="num">{num(p.avg_price)}</td>
              <PnLCell value={p.realized_pnl} />
              <PnLCell value={p.unrealized_pnl} />
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
