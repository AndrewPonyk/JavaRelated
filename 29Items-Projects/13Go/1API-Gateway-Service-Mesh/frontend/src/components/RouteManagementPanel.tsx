import { Activity, Plus, RefreshCw, Save, Trash2 } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  AnomalyEvent,
  GatewayRoute,
  RouteInput,
  Tenant,
  TrafficFeatures,
  createRoute,
  createTenant,
  deleteRoute,
  listAnomalies,
  listRoutes,
  listTenants,
  scoreTraffic,
  getAdminApiKey,
  setAdminApiKey,
  updateRoute
} from "../lib/api";

type LoadState = "loading" | "ready" | "error";

const emptyRoute = (tenantId: string): RouteInput => ({
  tenantId,
  name: "",
  host: "localhost:8080",
  pathPrefix: "/",
  methods: ["GET"],
  upstreamService: "http://httpbin.org",
  upstreamProtocol: "http",
  rateLimitPerMinute: 120,
  requiredScopes: [],
  transformHeaders: {},
  anomalyProtection: true
});

export function RouteManagementPanel() {
  const [status, setStatus] = useState<LoadState>("loading");
  const [error, setError] = useState("");
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState("");
  const [routes, setRoutes] = useState<GatewayRoute[]>([]);
  const [anomalies, setAnomalies] = useState<AnomalyEvent[]>([]);
  const [tenantName, setTenantName] = useState("");
  const [adminKey, setAdminKey] = useState(() => getAdminApiKey());
  const [editingRouteId, setEditingRouteId] = useState<string | null>(null);
  const [routeForm, setRouteForm] = useState<RouteInput>(emptyRoute(""));
  const [trafficForm, setTrafficForm] = useState<TrafficFeatures>({
    tenantId: "",
    routeId: "",
    requestRate: 100,
    errorRate: 0.02,
    p95LatencyMs: 180,
    bytesPerSec: 100000,
    deployVersion: "local"
  });
  const [decision, setDecision] = useState("");

  const selectedTenant = useMemo(
    () => tenants.find((tenant) => tenant.id === selectedTenantId),
    [tenants, selectedTenantId]
  );

  async function load(tenantId = selectedTenantId) {
    if (!getAdminApiKey()) {
      setStatus("ready");
      setError("Enter the admin API key to load gateway data.");
      return;
    }
    setStatus("loading");
    setError("");
    try {
      const loadedTenants = await listTenants();
      const nextTenantId = tenantId || loadedTenants[0]?.id || "";
      const [loadedRoutes, loadedAnomalies] = await Promise.all([
        nextTenantId ? listRoutes(nextTenantId) : Promise.resolve([]),
        nextTenantId ? listAnomalies(nextTenantId) : Promise.resolve([])
      ]);
      setTenants(loadedTenants);
      setSelectedTenantId(nextTenantId);
      setRoutes(loadedRoutes);
      setAnomalies(loadedAnomalies);
      setRouteForm((current) => ({ ...current, tenantId: nextTenantId || current.tenantId }));
      setTrafficForm((current) => ({ ...current, tenantId: nextTenantId, routeId: loadedRoutes[0]?.id || "" }));
      setStatus("ready");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load gateway data");
      setStatus("error");
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function handleCreateTenant(event: FormEvent) {
    event.preventDefault();
    if (tenantName.trim().length < 2) {
      setError("Tenant name must be at least 2 characters.");
      return;
    }
    const tenant = await createTenant({ name: tenantName.trim(), status: "active" });
    setTenantName("");
    await load(tenant.id);
  }

  function saveAdminKey() {
    setAdminApiKey(adminKey.trim());
    void load(selectedTenantId);
  }

  async function handleSaveRoute(event: FormEvent) {
    event.preventDefault();
    const validation = validateRoute(routeForm);
    if (validation) {
      setError(validation);
      return;
    }
    if (editingRouteId) {
      await updateRoute(editingRouteId, routeForm);
    } else {
      await createRoute(routeForm);
    }
    setEditingRouteId(null);
    setRouteForm(emptyRoute(selectedTenantId));
    await load(selectedTenantId);
  }

  async function handleDeleteRoute(id: string) {
    await deleteRoute(id);
    await load(selectedTenantId);
  }

  async function handleScoreTraffic(event: FormEvent) {
    event.preventDefault();
    if (!trafficForm.routeId || !trafficForm.tenantId) {
      setError("Select a tenant and route before scoring traffic.");
      return;
    }
    const result = await scoreTraffic(trafficForm);
    setDecision(`${result.isAnomaly ? "Anomaly" : "Normal"}: ${result.reason} (${result.score})`);
    await load(selectedTenantId);
  }

  function editRoute(route: GatewayRoute) {
    setEditingRouteId(route.id);
    setRouteForm({
      tenantId: route.tenantId,
      name: route.name,
      host: route.host,
      pathPrefix: route.pathPrefix,
      methods: route.methods,
      upstreamService: route.upstreamService,
      upstreamProtocol: route.upstreamProtocol,
      rateLimitPerMinute: route.rateLimitPerMinute,
      requiredScopes: route.requiredScopes,
      transformHeaders: route.transformHeaders ?? {},
      anomalyProtection: route.anomalyProtection
    });
  }

  return (
    <section className="workspace" aria-label="Gateway administration">
      <div className="toolbar">
        <div>
          <h2>Gateway Control Plane</h2>
          <p>{selectedTenant ? `Tenant: ${selectedTenant.name}` : "Create a tenant to begin."}</p>
        </div>
        <div className="key-entry">
          <input
            type="password"
            value={adminKey}
            onChange={(event) => setAdminKey(event.target.value)}
            placeholder="Admin API key"
            aria-label="Admin API key"
          />
          <button type="button" className="secondary" onClick={saveAdminKey}>Apply</button>
          <button className="icon-button" type="button" onClick={() => void load()} aria-label="Refresh">
            <RefreshCw size={18} />
          </button>
        </div>
      </div>

      {status === "loading" && <div className="status-row">Loading gateway state...</div>}
      {error && <div className="status-row error">{error}</div>}

      <div className="grid">
        <form className="panel" onSubmit={(event) => void handleCreateTenant(event)}>
          <h3>Tenants</h3>
          <label>
            Active tenant
            <select
              value={selectedTenantId}
              onChange={(event) => {
                setSelectedTenantId(event.target.value);
                setRouteForm(emptyRoute(event.target.value));
                void load(event.target.value);
              }}
            >
              {tenants.map((tenant) => (
                <option key={tenant.id} value={tenant.id}>
                  {tenant.name} ({tenant.status})
                </option>
              ))}
            </select>
          </label>
          <div className="inline-fields">
            <input value={tenantName} onChange={(event) => setTenantName(event.target.value)} placeholder="New tenant" />
            <button type="submit">
              <Plus size={16} />
              Add
            </button>
          </div>
        </form>

        <form className="panel wide" onSubmit={(event) => void handleSaveRoute(event)}>
          <h3>{editingRouteId ? "Edit Route" : "Create Route"}</h3>
          <div className="form-grid">
            <label>
              Name
              <input value={routeForm.name} onChange={(event) => setRouteForm({ ...routeForm, name: event.target.value })} />
            </label>
            <label>
              Host
              <input value={routeForm.host} onChange={(event) => setRouteForm({ ...routeForm, host: event.target.value })} />
            </label>
            <label>
              Path prefix
              <input value={routeForm.pathPrefix} onChange={(event) => setRouteForm({ ...routeForm, pathPrefix: event.target.value })} />
            </label>
            <label>
              Methods
              <input
                value={routeForm.methods.join(",")}
                onChange={(event) => setRouteForm({ ...routeForm, methods: splitCSV(event.target.value).map((value) => value.toUpperCase()) })}
              />
            </label>
            <label>
              Upstream URL
              <input value={routeForm.upstreamService} onChange={(event) => setRouteForm({ ...routeForm, upstreamService: event.target.value })} />
            </label>
            <label>
              Protocol
              <select value={routeForm.upstreamProtocol} onChange={(event) => setRouteForm({ ...routeForm, upstreamProtocol: event.target.value as "http" | "grpc" })}>
                <option value="http">http</option>
                <option value="grpc">grpc</option>
              </select>
            </label>
            <label>
              Rate limit/min
              <input
                type="number"
                min="0"
                value={routeForm.rateLimitPerMinute}
                onChange={(event) => setRouteForm({ ...routeForm, rateLimitPerMinute: Number(event.target.value) })}
              />
            </label>
            <label>
              Required scopes
              <input value={routeForm.requiredScopes.join(",")} onChange={(event) => setRouteForm({ ...routeForm, requiredScopes: splitCSV(event.target.value) })} />
            </label>
          </div>
          <label className="check-row">
            <input
              type="checkbox"
              checked={routeForm.anomalyProtection}
              onChange={(event) => setRouteForm({ ...routeForm, anomalyProtection: event.target.checked })}
            />
            Anomaly protection
          </label>
          <div className="actions">
            <button type="submit">
              <Save size={16} />
              {editingRouteId ? "Update route" : "Create route"}
            </button>
            {editingRouteId && (
              <button type="button" className="secondary" onClick={() => { setEditingRouteId(null); setRouteForm(emptyRoute(selectedTenantId)); }}>
                Cancel
              </button>
            )}
          </div>
        </form>
      </div>

      <div className="panel">
        <h3>Routes</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Match</th>
                <th>Upstream</th>
                <th>Limit</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {routes.length === 0 ? (
                <tr><td colSpan={5} className="empty-cell">No routes configured.</td></tr>
              ) : routes.map((route) => (
                <tr key={route.id}>
                  <td><strong>{route.name}</strong><span>{route.methods.join(", ")}</span></td>
                  <td><code>{route.host}</code><span>{route.pathPrefix}</span></td>
                  <td><code>{route.upstreamService}</code><span>{route.upstreamProtocol}</span></td>
                  <td>{route.rateLimitPerMinute}/min</td>
                  <td className="row-actions">
                    <button type="button" className="secondary" onClick={() => editRoute(route)}>Edit</button>
                    <button type="button" className="danger" onClick={() => void handleDeleteRoute(route.id)} aria-label={`Delete ${route.name}`}>
                      <Trash2 size={15} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="grid">
        <form className="panel" onSubmit={(event) => void handleScoreTraffic(event)}>
          <h3>Traffic Scoring</h3>
          <label>
            Route
            <select value={trafficForm.routeId} onChange={(event) => setTrafficForm({ ...trafficForm, routeId: event.target.value })}>
              {routes.map((route) => <option key={route.id} value={route.id}>{route.name}</option>)}
            </select>
          </label>
          <label>
            Error rate
            <input type="number" min="0" max="1" step="0.01" value={trafficForm.errorRate} onChange={(event) => setTrafficForm({ ...trafficForm, errorRate: Number(event.target.value) })} />
          </label>
          <label>
            p95 latency ms
            <input type="number" min="0" value={trafficForm.p95LatencyMs} onChange={(event) => setTrafficForm({ ...trafficForm, p95LatencyMs: Number(event.target.value) })} />
          </label>
          <button type="submit">
            <Activity size={16} />
            Score
          </button>
          {decision && <p className="decision">{decision}</p>}
        </form>

        <div className="panel wide">
          <h3>Anomaly Events</h3>
          <div className="event-list">
            {anomalies.length === 0 ? (
              <p>No anomaly events.</p>
            ) : anomalies.map((event) => (
              <div className="event-row" key={event.id}>
                <strong>{event.reason}</strong>
                <span>score {event.score} · {new Date(event.observedAt).toLocaleString()}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function splitCSV(value: string): string[] {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

function validateRoute(route: RouteInput): string {
  if (!route.tenantId) return "Select a tenant before saving a route.";
  if (route.name.trim().length < 2) return "Route name must be at least 2 characters.";
  if (!route.host.trim()) return "Host is required.";
  if (!route.pathPrefix.startsWith("/")) return "Path prefix must start with /.";
  if (route.methods.length === 0) return "At least one HTTP method is required.";
  if (route.upstreamProtocol === "http" && !route.upstreamService.startsWith("http")) return "HTTP routes require an absolute upstream URL.";
  return "";
}
