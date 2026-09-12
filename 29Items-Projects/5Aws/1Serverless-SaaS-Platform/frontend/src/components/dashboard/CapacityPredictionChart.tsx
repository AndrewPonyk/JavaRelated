import React, { useEffect, useState } from 'react';
import { fetchCapacityPrediction, fetchUsageHistory } from '../../api/usage';
import { CapacityPrediction, UsageHistoryPoint } from '../../types/usage';
import { LoadingSpinner } from '../common/LoadingSpinner';

interface Props {
  tenantId: string;
}

export const CapacityPredictionChart: React.FC<Props> = ({ tenantId }) => {
  const [prediction, setPrediction] = useState<CapacityPrediction | null>(null);
  const [history, setHistory] = useState<UsageHistoryPoint[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setIsLoading(true);
    setError(null);
    Promise.all([fetchCapacityPrediction('api_calls'), fetchUsageHistory('api_calls', 7)])
      .then(([pred, hist]) => {
        setPrediction(pred);
        setHistory(hist.points);
      })
      .catch((err: unknown) => {
        const msg = err instanceof Error ? err.message : 'Prediction error';
        setError(msg);
      })
      .finally(() => setIsLoading(false));
  }, [tenantId]);

  if (isLoading) {
    return (
      <div className="card">
        <LoadingSpinner label="Querying SageMaker forecast model..." />
      </div>
    );
  }

  if (error || !prediction) {
    return (
      <div className="card" style={{ borderColor: 'var(--danger)' }}>
        <h3 className="card-title" style={{ color: 'var(--danger)' }}>Prediction Error</h3>
        <p style={{ color: 'var(--text-secondary)', marginTop: '0.5rem' }}>{error || 'Unable to retrieve forecast.'}</p>
      </div>
    );
  }

  const { forecast_next_7_days, forecast_next_30_days, model_version, anomaly_risk, recommended_tier_upgrade } =
    prediction;

  // Build SVG points for chart
  const sampleValues = history.length > 0
    ? history.map((h) => h.count)
    : [28000, 31000, 30500, 34000, 33000, 37000, 39000];

  const maxVal = Math.max(...sampleValues, forecast_next_7_days / 7) * 1.25 || 50000;
  const chartHeight = 120;
  const chartWidth = 320;
  const stepX = chartWidth / (sampleValues.length - 1 || 1);

  const polyPoints = sampleValues
    .map((val, idx) => {
      const x = idx * stepX;
      const y = chartHeight - (val / maxVal) * chartHeight;
      return `${x},${y}`;
    })
    .join(' ');

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h3 className="card-title">ML-Powered Capacity Forecasting</h3>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Model: {model_version} &bull; SageMaker Serverless Inference
          </span>
        </div>
        <span
          className={`status-badge ${
            anomaly_risk === 'HIGH' ? 'status-danger' : anomaly_risk === 'MEDIUM' ? 'status-warning' : 'status-normal'
          }`}
        >
          {anomaly_risk} Risk
        </span>
      </div>

      {/* SVG Time-Series Chart */}
      <div style={{ marginTop: '0.5rem', marginBottom: '1rem' }}>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.25rem' }}>
          Historical Ingestion (7-Day Sample vs ML Trajectory)
        </div>
        <svg
          viewBox={`0 0 ${chartWidth} ${chartHeight}`}
          style={{ width: '100%', height: '110px', overflow: 'visible' }}
        >
          <defs>
            <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#6366f1" stopOpacity="0.4" />
              <stop offset="100%" stopColor="#6366f1" stopOpacity="0.0" />
            </linearGradient>
          </defs>
          <polygon
            fill="url(#chartGradient)"
            points={`0,${chartHeight} ${polyPoints} ${chartWidth},${chartHeight}`}
          />
          <polyline
            fill="none"
            stroke="var(--accent-primary)"
            strokeWidth="3"
            strokeLinecap="round"
            points={polyPoints}
          />
          {sampleValues.map((val, idx) => {
            const x = idx * stepX;
            const y = chartHeight - (val / maxVal) * chartHeight;
            return <circle key={idx} cx={x} cy={y} r="4" fill="#a855f7" />;
          })}
        </svg>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginTop: '0.5rem' }}>
        <div style={{ background: 'var(--bg-surface-elevated)', padding: '0.85rem', borderRadius: '0.5rem' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Projected 7-Day Additional</div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--accent-primary)' }}>
            +{forecast_next_7_days.toLocaleString()}
          </div>
        </div>

        <div style={{ background: 'var(--bg-surface-elevated)', padding: '0.85rem', borderRadius: '0.5rem' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Projected 30-Day Total</div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, fontFamily: 'var(--font-mono)', color: '#38bdf8' }}>
            {forecast_next_30_days.toLocaleString()}
          </div>
        </div>
      </div>

      {recommended_tier_upgrade && (
        <div
          style={{
            marginTop: '1rem',
            padding: '0.65rem 0.85rem',
            borderRadius: '0.375rem',
            background: 'rgba(245, 158, 11, 0.12)',
            border: '1px solid rgba(245, 158, 11, 0.3)',
            color: 'var(--warning)',
            fontSize: '0.8rem',
          }}
        >
          <strong>Capacity Alert:</strong> Projected consumption exceeds monthly tier quota. Upgrade recommended.
        </div>
      )}
    </div>
  );
};
