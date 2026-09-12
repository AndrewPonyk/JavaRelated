import type { BalancePoint } from '../dashboard.api';

interface BalanceTrendCardProps {
  history: BalancePoint[];
  currency: string;
}

const WIDTH = 200;
const HEIGHT = 48;
const PADDING = 4;

/**
 * Dependency-free SVG sparkline of month-end balances. A chart library would blow the
 * Lighthouse script budget for one line — see docs/ARCHITECTURE.md §2.4.
 */
export function BalanceTrendCard({ history, currency }: BalanceTrendCardProps) {
  if (history.length < 2) {
    return null; // a trend needs at least two points; the balance card still shows the value
  }

  const balances = history.map((point) => point.balance);
  const min = Math.min(...balances);
  const max = Math.max(...balances);
  const span = max - min || 1; // flat history → centered line, not division by zero

  const points = history
    .map((point, index) => {
      const x = PADDING + (index / (history.length - 1)) * (WIDTH - 2 * PADDING);
      const y = HEIGHT - PADDING - ((point.balance - min) / span) * (HEIGHT - 2 * PADDING);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');

  const first = history[0]!;
  const last = history[history.length - 1]!;
  const delta = last.balance - first.balance;
  const format = new Intl.NumberFormat(undefined, { style: 'currency', currency });
  const trendLabel = `Balance ${delta >= 0 ? 'up' : 'down'} ${format.format(Math.abs(delta))} since ${first.month}`;

  return (
    <article className="summary-card">
      <h3 className="summary-card__label">Balance trend</h3>
      <svg
        className="sparkline"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={trendLabel}
        preserveAspectRatio="none"
      >
        <polyline
          points={points}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <p className="summary-card__hint">{trendLabel}</p>
    </article>
  );
}
