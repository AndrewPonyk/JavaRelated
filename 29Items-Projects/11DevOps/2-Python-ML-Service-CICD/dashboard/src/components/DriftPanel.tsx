import type { DriftReportRecord, DriftSummary } from "../api/types";

interface Props {
  drift: DriftSummary | null;
  reports: DriftReportRecord[];
  loading: boolean;
  error: string | null;
  onEvaluate: () => void;
}

const STATUS_LABEL: Record<DriftSummary["status"], string> = {
  ok: "Stable",
  drift_detected: "Drift detected",
  insufficient_data: "Insufficient data",
};

/** PSI drift status per monitored feature plus recent report history. */
export default function DriftPanel({ drift, reports, loading, error, onEvaluate }: Props) {
  return (
    <section className="card" aria-busy={loading}>
      <header className="card-header">
        <h2>Feature drift</h2>
        <button type="button" onClick={onEvaluate} disabled={loading}>
          {loading ? "Evaluating…" : "Evaluate now"}
        </button>
      </header>

      {error && <p className="error-banner">{error}</p>}
      {loading && !drift && <div className="skeleton" />}

      {drift && (
        <>
          <p>
            <span className={`badge ${drift.status}`}>{STATUS_LABEL[drift.status]}</span>{" "}
            <span className="muted">
              over {drift.evaluated_rows} recent predictions
              {drift.retraining_triggered && " · retraining triggered"}
            </span>
          </p>
          {drift.detail && <p className="muted">{drift.detail}</p>}

          {drift.features.map((feature) => {
            const ratio = Math.min(feature.psi_score / (feature.threshold * 2), 1);
            return (
              <div className="psi-row" key={feature.feature_name}>
                <span className="psi-name">{feature.feature_name}</span>
                <div className="psi-track">
                  <div
                    className={`psi-bar ${feature.drift_detected ? "drifted" : ""}`}
                    style={{ width: `${Math.max(ratio * 100, 2)}%` }}
                  />
                  <div className="psi-threshold" />
                </div>
                <span className="psi-value">{feature.psi_score.toFixed(3)}</span>
              </div>
            );
          })}
        </>
      )}

      {reports.length > 0 && (
        <details>
          <summary>Recent reports ({reports.length})</summary>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Feature</th>
                  <th>PSI</th>
                  <th>Drift</th>
                  <th>Checked at</th>
                </tr>
              </thead>
              <tbody>
                {reports.map((report) => (
                  <tr key={report.id}>
                    <td>{report.feature_name}</td>
                    <td>{report.psi_score.toFixed(3)}</td>
                    <td>{report.drift_detected ? "yes" : "no"}</td>
                    <td>{new Date(report.created_at).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      )}
    </section>
  );
}
