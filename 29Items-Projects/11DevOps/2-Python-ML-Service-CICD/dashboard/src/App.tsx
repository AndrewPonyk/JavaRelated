import ModelMonitoringDashboard from "./components/ModelMonitoringDashboard";

export default function App() {
  return (
    <div className="page">
      <header className="page-header">
        <h1>Fraud Detection — Model Monitoring</h1>
        <p className="muted">
          Champion/challenger catalog, A/B traffic control, PSI drift and live scoring.
        </p>
      </header>
      <main>
        <ModelMonitoringDashboard />
      </main>
    </div>
  );
}
