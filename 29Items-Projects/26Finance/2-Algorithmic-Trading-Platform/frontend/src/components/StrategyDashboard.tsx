// StrategyDashboard — lists strategies with live state, a kill-switch, and a
// create form. Demonstrates the project's data-fetch + loading/error/empty pattern.

import { useState } from 'react';

import { useStrategies } from '../hooks/useStrategies';
import type { Strategy, StrategyState } from '../types/strategy';
import CreateStrategyForm from './CreateStrategyForm';

const STATE_COLORS: Record<StrategyState, string> = {
  DRAFT: '#9ca3af',
  BACKTESTING: '#3b82f6',
  PAPER: '#f59e0b',
  LIVE: '#10b981',
  HALTED: '#ef4444',
};

function StateBadge({ state }: { state: StrategyState }) {
  return (
    <span className="badge" style={{ background: STATE_COLORS[state] }}>
      {state}
    </span>
  );
}

function StrategyRow({
  strategy,
  onHalt,
  onRemove,
  busy,
}: {
  strategy: Strategy;
  onHalt: (id: string) => void;
  onRemove: (id: string) => void;
  busy: boolean;
}) {
  const canHalt = strategy.state === 'LIVE' || strategy.state === 'PAPER';
  const canDelete = strategy.state !== 'LIVE';
  return (
    <tr>
      <td>{strategy.name}</td>
      <td>{strategy.symbols.join(', ')}</td>
      <td>
        <StateBadge state={strategy.state} />
      </td>
      <td className="num">{strategy.max_position_qty.toLocaleString()}</td>
      <td className="actions">
        <button onClick={() => onHalt(strategy.id)} disabled={!canHalt || busy} className="danger">
          {busy ? '…' : 'Halt'}
        </button>
        <button onClick={() => onRemove(strategy.id)} disabled={!canDelete || busy}>
          Delete
        </button>
      </td>
    </tr>
  );
}

export default function StrategyDashboard() {
  const { strategies, loading, error, refetch, halt, create, remove } = useStrategies();
  const [busyId, setBusyId] = useState<string | null>(null);

  const handleHalt = async (id: string) => {
    if (!window.confirm('Halt this strategy? It will stop trading immediately.')) return;
    setBusyId(id);
    try {
      await halt(id);
    } catch {
      alert('Failed to halt strategy. Check connectivity and retry.');
    } finally {
      setBusyId(null);
    }
  };

  const handleRemove = async (id: string) => {
    if (!window.confirm('Delete this strategy?')) return;
    setBusyId(id);
    try {
      await remove(id);
    } catch {
      alert('Failed to delete strategy.');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="grid-2">
      <section className="card">
        <header className="card-header">
          <h3>Strategies</h3>
          <button onClick={refetch}>Refresh</button>
        </header>

        {loading && <div className="muted">Loading strategies…</div>}
        {error && (
          <div className="banner error">
            Could not load strategies: {error} <button onClick={refetch}>Retry</button>
          </div>
        )}
        {!loading && !error && strategies.length === 0 && (
          <div className="muted">No strategies yet. Create one to get started.</div>
        )}
        {strategies.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Symbols</th>
                <th>State</th>
                <th className="num">Max Pos</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {strategies.map((s) => (
                <StrategyRow
                  key={s.id}
                  strategy={s}
                  onHalt={handleHalt}
                  onRemove={handleRemove}
                  busy={busyId === s.id}
                />
              ))}
            </tbody>
          </table>
        )}
      </section>

      <CreateStrategyForm onCreate={create} />
    </div>
  );
}
