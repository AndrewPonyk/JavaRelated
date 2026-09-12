// Live PnL summary strip.

import type { PnLSummary } from '../types/strategy';

function money(value: string): string {
  const n = Number(value);
  return n.toLocaleString(undefined, { style: 'currency', currency: 'USD' });
}

function Metric({ label, value, signed }: { label: string; value: string; signed?: boolean }) {
  const n = Number(value);
  const cls = signed ? (n > 0 ? 'pos' : n < 0 ? 'neg' : '') : '';
  return (
    <div className="metric">
      <span className="metric-label">{label}</span>
      <span className={`metric-value ${cls}`}>{money(value)}</span>
    </div>
  );
}

export default function PnLBar({ pnl }: { pnl: PnLSummary | null }) {
  if (!pnl) return <div className="pnl-bar muted">PnL: —</div>;
  return (
    <div className="pnl-bar">
      <Metric label="Total PnL" value={pnl.total_pnl} signed />
      <Metric label="Realized" value={pnl.realized_pnl} signed />
      <Metric label="Unrealized" value={pnl.unrealized_pnl} signed />
      <div className="metric">
        <span className="metric-label">Open positions</span>
        <span className="metric-value">{pnl.open_positions}</span>
      </div>
    </div>
  );
}
