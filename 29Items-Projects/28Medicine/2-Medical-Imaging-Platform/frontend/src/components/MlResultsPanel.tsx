// Diagnostic-assist overlay: per-pathology probabilities for a study.
// Decision support only — clearly labelled, never auto-diagnosing.

import { useMlResults } from '@/hooks/useStudies';

interface MlResultsPanelProps {
  studyInstanceUid: string;
  threshold?: number; // highlight findings above this probability
}

export function MlResultsPanel({ studyInstanceUid, threshold = 0.5 }: MlResultsPanelProps) {
  const { data, isLoading, isError } = useMlResults(studyInstanceUid);

  if (isLoading) return <aside className="ml-panel">Running analysis…</aside>;
  if (isError) return <aside className="ml-panel ml-panel--error">Analysis unavailable.</aside>;
  if (!data || data.length === 0)
    return <aside className="ml-panel">No ML results for this study yet.</aside>;

  const latest = data[0];
  const findings = Object.entries(latest.predictions).sort((a, b) => b[1] - a[1]);

  return (
    <aside className="ml-panel">
      <header>
        <h3>AI Assist — chest X-ray</h3>
        <small>
          {latest.model_name} v{latest.model_version} · decision support only
        </small>
      </header>
      <ul className="ml-panel__findings">
        {findings.map(([label, prob]) => (
          <li key={label} className={prob >= threshold ? 'is-flagged' : ''}>
            <span className="ml-panel__label">{label}</span>
            <progress value={prob} max={1} />
            <span className="ml-panel__score">{(prob * 100).toFixed(0)}%</span>
          </li>
        ))}
      </ul>
    </aside>
  );
}
