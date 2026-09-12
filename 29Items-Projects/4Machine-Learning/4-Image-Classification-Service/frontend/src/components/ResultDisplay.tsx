// Renders ranked label predictions with confidence bars.
import type { ClassifyResponse } from '../api/client';
import { formatScore } from '../utils/format';

interface Props {
  result: ClassifyResponse;
}

export function ResultDisplay({ result }: Props) {
  if (result.labels.length === 0) {
    return <p className="muted">No categories matched above their confidence threshold.</p>;
  }

  return (
    <div className="results">
      <p className="muted meta">
        model {result.model_version} · {result.cached ? 'cached' : 'fresh'}
        {result.latency_ms != null && ` · ${result.latency_ms.toFixed(1)} ms`}
      </p>
      <ul className="label-list">
        {result.labels.map((label) => (
          <li key={label.name} className="label-row">
            <div className="label-head">
              <span>{label.name}</span>
              <span>{formatScore(label.score)}</span>
            </div>
            <div className="bar">
              <div className="bar-fill" style={{ width: formatScore(label.score) }} />
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
