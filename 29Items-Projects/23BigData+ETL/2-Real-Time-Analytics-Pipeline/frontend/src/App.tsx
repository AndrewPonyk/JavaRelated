import { useState } from 'react';
import AnomalyAlertsPanel from './components/AnomalyAlertsPanel';
import CreateMetricForm from './components/CreateMetricForm';
import MetricsDashboard from './components/MetricsDashboard';
import { useMetricsStream } from './hooks/useMetricsStream';

export default function App() {
  const { status, lastAggregate, alertsVersion } = useMetricsStream();
  const [defsVersion, setDefsVersion] = useState(0);

  return (
    <>
      <header className="app-header">
        <h1>RTAP</h1>
        <span className="sub">Real-time business metrics</span>
        <span style={{ flex: 1 }} />
        <span className={`pill ${status}`} role="status">
          <span className="dot" aria-hidden="true" />
          {status === 'live' ? 'Live' : status === 'paused' ? 'Live paused — reconnecting' : 'Connecting…'}
        </span>
      </header>
      <main className="app-main">
        <MetricsDashboard defsVersion={defsVersion} live={lastAggregate} />
        <div className="side-column">
          <AnomalyAlertsPanel alertsVersion={alertsVersion} />
          <CreateMetricForm onCreated={() => setDefsVersion((v) => v + 1)} />
        </div>
      </main>
    </>
  );
}
