import { RouteManagementPanel } from "./components/RouteManagementPanel";

export default function App() {
  return (
    <main className="app-shell">
      <section className="page-header">
        <div>
          <h1>API Gateway Admin</h1>
          <p>Tenants, routes, anomaly scoring, and mesh-aware traffic controls.</p>
        </div>
      </section>
      <RouteManagementPanel />
    </main>
  );
}
