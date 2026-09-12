import React, { useState } from 'react';
import { useUsageMetrics } from '../../hooks/useUsageMetrics';
import { ingestUsageEvent } from '../../api/usage';
import { LoadingSpinner } from '../common/LoadingSpinner';

interface UsageMetricsCardProps {
  tenantId?: string;
  metric?: string;
}

export const UsageMetricsCard: React.FC<UsageMetricsCardProps> = ({
  tenantId = 'tenant-alpha-enterprise',
  metric = 'api_calls',
}) => {
  const { metrics, invoice, isLoading, error, refetch } = useUsageMetrics(metric);
  const [isSimulating, setIsSimulating] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const handleSimulateBurst = async () => {
    try {
      setIsSimulating(true);
      await ingestUsageEvent(metric, 25000);
      setToastMessage('Successfully ingested 25,000 metered events!');
      setTimeout(() => setToastMessage(null), 4000);
      await refetch();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Simulation failed';
      setToastMessage(`Error: ${msg}`);
    } finally {
      setIsSimulating(false);
    }
  };

  // 1. Loading State
  if (isLoading) {
    return (
      <div className="card">
        <LoadingSpinner label="Loading real-time usage metrics..." />
      </div>
    );
  }

  // 2. Error State
  if (error || !metrics) {
    return (
      <div className="card" style={{ borderColor: 'var(--danger)' }}>
        <div className="card-header">
          <h3 className="card-title" style={{ color: 'var(--danger)' }}>Metrics Unavailable</h3>
        </div>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }}>
          {error || 'Could not retrieve usage metrics for this tenant.'}
        </p>
        <button className="btn btn-primary" onClick={() => refetch()}>
          Retry Request
        </button>
      </div>
    );
  }

  // 3. Display Pattern
  const { total_count, quota_limit, percent_consumed, warning_threshold_exceeded, period } = metrics;
  const isCritical = percent_consumed >= 100;
  const isWarning = warning_threshold_exceeded && !isCritical;

  const getStatusBadge = () => {
    if (isCritical) return <span className="status-badge status-danger">Quota Exceeded (100%)</span>;
    if (isWarning) return <span className="status-badge status-warning">Warning (&gt;80%)</span>;
    return <span className="status-badge status-normal">Healthy</span>;
  };

  const getProgressBarColor = () => {
    if (isCritical) return 'var(--danger)';
    if (isWarning) return 'var(--warning)';
    return 'var(--success)';
  };

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h3 className="card-title">Real-Time Usage Metering</h3>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Period: {period} &bull; Tenant: {tenantId}
          </span>
        </div>
        {getStatusBadge()}
      </div>

      <div className="metric-highlight">
        {total_count.toLocaleString()}
        <span style={{ fontSize: '1rem', color: 'var(--text-muted)', fontWeight: 400, marginLeft: '0.5rem' }}>
          / {quota_limit.toLocaleString()} units
        </span>
      </div>

      <div className="progress-container">
        <div
          className="progress-bar"
          style={{
            width: `${Math.min(percent_consumed, 100)}%`,
            backgroundColor: getProgressBarColor(),
          }}
        />
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
        <span>{percent_consumed}% of monthly quota consumed</span>
        {invoice && <span>Current Bill: ${(invoice.total_due_cents / 100).toFixed(2)}</span>}
      </div>

      <div style={{ marginTop: '1.5rem', display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
        <button
          className="btn btn-primary"
          onClick={handleSimulateBurst}
          disabled={isSimulating}
        >
          {isSimulating ? 'Ingesting...' : 'Simulate API Call (+25k)'}
        </button>
        <button
          className="btn"
          style={{ background: 'var(--bg-surface-elevated)', color: 'var(--text-primary)' }}
          onClick={() => refetch()}
        >
          Refresh
        </button>
      </div>

      {toastMessage && (
        <div
          style={{
            marginTop: '1rem',
            padding: '0.5rem 0.75rem',
            borderRadius: '0.375rem',
            background: 'var(--bg-surface-elevated)',
            borderLeft: '4px solid var(--accent-primary)',
            fontSize: '0.85rem',
          }}
        >
          {toastMessage}
        </div>
      )}
    </div>
  );
};
