import type { SearchResponse } from "@/types";

interface Props {
  data: SearchResponse | null;
  loading: boolean;
  error: string | null;
}

/** Presentational component: renders the loading / error / empty / data states. */
export function SearchResults({ data, loading, error }: Props) {
  if (loading) {
    return <p className="muted">Loading results…</p>;
  }
  if (error) {
    return (
      <p role="alert" className="alert">
        ⚠ {error}
      </p>
    );
  }
  if (!data) {
    return null;
  }
  if (data.results.length === 0) {
    return <p className="muted">No results for “{data.query}”.</p>;
  }

  return (
    <div className="stack">
      <p className="muted" style={{ fontSize: 13 }}>
        {data.count} results · backend {data.backend} · {data.mode}
        {data.took_ms != null ? ` · ${data.took_ms} ms` : ""}
      </p>
      <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0 }}>
        {data.results.map((r) => (
          <li key={r.id} className="card">
            <div className="result-head">
              <span>{r.id}</span>
              <span>score {r.score.toFixed(4)}</span>
            </div>
            <p style={{ margin: "6px 0 0" }}>{r.text || "(no snippet)"}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
