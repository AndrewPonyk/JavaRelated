import { useEffect, useRef, useState } from 'react';
import { getAggregates, getMetricDefinitions } from '../api/client';
import type { AggregatePoint, LiveAggregate, MetricDefinition, WindowSize } from '../types/metrics';

const WINDOWS: WindowSize[] = ['1s', '10s', '1m', '5m', '1h'];
const LIVE_REFRESH_MS = 2_000; // debounce for SSE-driven series refresh
const compact = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 });

const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour12: false });

interface Props {
  /** Bumped by App when a new metric definition is registered. */
  defsVersion: number;
  /** Latest SSE aggregate (per metric series); merged into the live tile + debounced refetch. */
  live: LiveAggregate | null;
}

/**
 * Live metric dashboard: fetch → loading / error / empty / data states,
 * stat tiles (live-updated from SSE), a single-series sparkline (title names the
 * series — no legend needed for one series), and a table view as the non-visual channel.
 */
export default function MetricsDashboard({ defsVersion, live }: Props) {
  const [definitions, setDefinitions] = useState<MetricDefinition[]>([]);
  const [metricKey, setMetricKey] = useState('');
  const [windowSize, setWindowSize] = useState<WindowSize>('1m');
  const [points, setPoints] = useState<AggregatePoint[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  // Live "current window" accumulator: SSE pushes one record per dimension series,
  // so counts for the same window are summed client-side for the tile.
  const liveWindowsRef = useRef(new Map<number, { count: number; sum: number }>());
  const [liveTile, setLiveTile] = useState<{ windowStart: number; count: number } | null>(null);
  const refetchTimer = useRef<number | null>(null);

  // Load metric definitions (re-runs when a metric is created).
  useEffect(() => {
    const ctrl = new AbortController();
    getMetricDefinitions(ctrl.signal)
      .then((defs) => {
        setDefinitions(defs);
        setMetricKey((prev) => prev || defs[0]?.metricKey || '');
      })
      .catch((e: unknown) => {
        if (!ctrl.signal.aborted) setError(e instanceof Error ? e.message : 'Failed to load metrics');
      });
    return () => ctrl.abort();
  }, [defsVersion]);

  // (Re)load the series. Refetches are silent (no skeleton) — the skeleton only
  // shows while points === null, which selection changes set explicitly.
  useEffect(() => {
    if (!metricKey) return;
    const ctrl = new AbortController();
    setError(null);
    getAggregates(metricKey, windowSize, ctrl.signal)
      .then(setPoints)
      .catch((e: unknown) => {
        if (!ctrl.signal.aborted) setError(e instanceof Error ? e.message : 'Failed to load series');
      });
    return () => ctrl.abort();
  }, [metricKey, windowSize, reloadToken]);

  // Reset live accumulation when the selection changes.
  useEffect(() => {
    liveWindowsRef.current.clear();
    setLiveTile(null);
  }, [metricKey, windowSize]);

  // Fold matching SSE aggregates into the live tile + schedule a debounced refetch.
  useEffect(() => {
    if (!live || live.metricKey !== metricKey || live.windowSize !== windowSize) return;
    const acc = liveWindowsRef.current.get(live.windowStart) ?? { count: 0, sum: 0 };
    const next = { count: acc.count + live.count, sum: acc.sum + live.sum };
    liveWindowsRef.current.set(live.windowStart, next);
    if (liveWindowsRef.current.size > 300) {
      // bounded memory on long-running sessions — only the newest windows matter
      const oldest = Math.min(...liveWindowsRef.current.keys());
      liveWindowsRef.current.delete(oldest);
    }
    setLiveTile((prev) =>
      prev && prev.windowStart > live.windowStart ? prev : { windowStart: live.windowStart, count: next.count },
    );
    if (refetchTimer.current === null) {
      refetchTimer.current = window.setTimeout(() => {
        refetchTimer.current = null;
        setReloadToken((t) => t + 1);
      }, LIVE_REFRESH_MS);
    }
  }, [live, metricKey, windowSize]);

  useEffect(
    () => () => {
      if (refetchTimer.current !== null) window.clearTimeout(refetchTimer.current);
    },
    [],
  );

  const selectMetric = (key: string) => {
    setMetricKey(key);
    setPoints(null); // selection change → skeleton
  };
  const selectWindow = (w: WindowSize) => {
    setWindowSize(w);
    setPoints(null);
  };
  const retry = () => {
    setPoints(null);
    setReloadToken((t) => t + 1);
  };

  const definition = definitions.find((d) => d.metricKey === metricKey);
  const title = definition ? definition.displayName : metricKey || 'Metrics';

  const totalEvents = points?.reduce((acc, p) => acc + p.count, 0) ?? 0;
  const peak = points?.length ? Math.max(...points.map((p) => p.count)) : 0;
  const last = points?.at(-1);
  const lastFetchedMs = last ? new Date(last.windowStart).getTime() : 0;
  const lastWindowCount =
    liveTile && liveTile.windowStart >= lastFetchedMs ? liveTile.count : (last?.count ?? 0);
  const prev = points && points.length > 1 ? points[points.length - 2] : undefined;
  const deltaPct =
    last && prev && prev.count > 0 ? ((last.count - prev.count) / prev.count) * 100 : null;

  return (
    <section className="panel" aria-label="Metric dashboard">
      <h2>
        {title} — events per {windowSize} window
      </h2>

      {/* Filters in one row above the chart */}
      <div className="filter-row">
        <select aria-label="Metric" value={metricKey} onChange={(e) => selectMetric(e.target.value)}>
          {definitions.map((d) => (
            <option key={d.metricKey} value={d.metricKey}>
              {d.displayName}
            </option>
          ))}
        </select>
        <div className="seg" role="group" aria-label="Window size">
          {WINDOWS.map((w) => (
            <button key={w} aria-pressed={w === windowSize} onClick={() => selectWindow(w)}>
              {w}
            </button>
          ))}
        </div>
      </div>

      {error ? (
        <div className="state error" role="alert">
          {error}
          <button onClick={retry}>Retry</button>
        </div>
      ) : points === null ? (
        <div className="skeleton" aria-label="Loading series" />
      ) : points.length === 0 ? (
        <div className="state">No data in the selected range yet — start the pipeline and the seeder.</div>
      ) : (
        <>
          <div className="tiles">
            <div className="tile">
              <div className="label">Events (range)</div>
              <div className="value">{compact.format(totalEvents)}</div>
              {deltaPct !== null && (
                <div className={`delta ${deltaPct >= 0 ? 'up' : 'down'}`}>
                  {deltaPct >= 0 ? '↑' : '↓'} {Math.abs(deltaPct).toFixed(1)}% vs previous window
                </div>
              )}
            </div>
            <div className="tile">
              <div className="label">Last window</div>
              <div className="value">{compact.format(lastWindowCount)}</div>
            </div>
            <div className="tile">
              <div className="label">Peak window</div>
              <div className="value">{compact.format(peak)}</div>
            </div>
          </div>

          <Sparkline points={points} />

          <table className="data" aria-label="Recent windows">
            <thead>
              <tr>
                <th>Window start</th>
                <th>Events</th>
                <th>Avg</th>
                <th>Min</th>
                <th>Max</th>
              </tr>
            </thead>
            <tbody>
              {points
                .slice(-8)
                .reverse()
                .map((p) => (
                  <tr key={p.windowStart}>
                    <td>{fmtTime(p.windowStart)}</td>
                    <td>{p.count.toLocaleString()}</td>
                    <td>{p.avg.toFixed(2)}</td>
                    <td>{p.min.toFixed(2)}</td>
                    <td>{p.max.toFixed(2)}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}

/**
 * Single-series sparkline. Mark spec: 2px round-joined line, 10%-opacity area
 * wash, ≥8px end marker with a 2px surface ring, hairline baseline, and a
 * crosshair + tooltip hover layer.
 */
function Sparkline({ points }: { points: AggregatePoint[] }) {
  const [hover, setHover] = useState<number | null>(null);

  const W = 640;
  const H = 140;
  const PAD = 8;

  const counts = points.map((p) => p.count);
  const minV = Math.min(...counts);
  const span = Math.max(...counts) - minV || 1;
  const xAt = (i: number) => PAD + (i * (W - 2 * PAD)) / Math.max(points.length - 1, 1);
  const yAt = (v: number) => H - PAD - ((v - minV) * (H - 2 * PAD)) / span;

  const line = points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${xAt(i).toFixed(1)},${yAt(p.count).toFixed(1)}`)
    .join(' ');
  const area = `${line} L${xAt(points.length - 1).toFixed(1)},${H - PAD} L${xAt(0).toFixed(1)},${H - PAD} Z`;
  const lastIdx = points.length - 1;

  const onPointerMove = (e: React.PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * W;
    const idx = Math.round(((px - PAD) / (W - 2 * PAD)) * (points.length - 1));
    setHover(Math.min(Math.max(idx, 0), points.length - 1));
  };

  return (
    <div className="chart-wrap">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        width="100%"
        role="img"
        aria-label={`Events per window, ${points.length} windows; values also in the table below`}
        onPointerMove={onPointerMove}
        onPointerLeave={() => setHover(null)}
      >
        <line x1={PAD} y1={H - PAD} x2={W - PAD} y2={H - PAD} stroke="var(--baseline)" strokeWidth="1" />
        <path d={area} fill="var(--series-1)" fillOpacity="0.1" />
        <path
          d={line}
          fill="none"
          stroke="var(--series-1)"
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        {hover !== null && (
          <line
            x1={xAt(hover)}
            y1={PAD}
            x2={xAt(hover)}
            y2={H - PAD}
            stroke="var(--grid)"
            strokeWidth="1"
          />
        )}
        {hover !== null && (
          <circle
            cx={xAt(hover)}
            cy={yAt(points[hover].count)}
            r="4"
            fill="var(--series-1)"
            stroke="var(--surface-1)"
            strokeWidth="2"
          />
        )}
        <circle
          cx={xAt(lastIdx)}
          cy={yAt(points[lastIdx].count)}
          r="4"
          fill="var(--series-1)"
          stroke="var(--surface-1)"
          strokeWidth="2"
        />
      </svg>
      {hover !== null && (
        <div
          className="chart-tooltip"
          style={{
            left: `${(xAt(hover) / W) * 100}%`,
            top: `${(yAt(points[hover].count) / H) * 100}%`,
          }}
        >
          {fmtTime(points[hover].windowStart)} ·{' '}
          <span className="val">{points[hover].count.toLocaleString()} events</span>
        </div>
      )}
    </div>
  );
}
