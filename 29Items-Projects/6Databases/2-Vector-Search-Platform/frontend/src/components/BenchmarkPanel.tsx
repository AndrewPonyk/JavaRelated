import type { BenchmarkResult } from "@/types";

interface Props {
  results: BenchmarkResult[];
}

/** Presentational comparison table for recall@k benchmark runs across backends. */
export function BenchmarkPanel({ results }: Props) {
  if (results.length === 0) {
    return <p className="muted">No benchmark results yet.</p>;
  }
  const fmt = (n: number) => (n < 0 ? "—" : n.toFixed(3));
  return (
    <table className="table">
      <thead>
        <tr>
          <th>Backend</th>
          <th>recall@{results[0]?.k}</th>
          <th>MRR</th>
          <th>p50 (ms)</th>
          <th>p95 (ms)</th>
          <th>QPS</th>
        </tr>
      </thead>
      <tbody>
        {results.map((r) => (
          <tr key={r.backend}>
            <td>{r.backend}</td>
            <td>{fmt(r.recall_at_k)}</td>
            <td>{fmt(r.mrr)}</td>
            <td>{r.latency_p50_ms < 0 ? "—" : r.latency_p50_ms.toFixed(1)}</td>
            <td>{r.latency_p95_ms < 0 ? "—" : r.latency_p95_ms.toFixed(1)}</td>
            <td>{r.qps < 0 ? "—" : r.qps.toFixed(1)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
