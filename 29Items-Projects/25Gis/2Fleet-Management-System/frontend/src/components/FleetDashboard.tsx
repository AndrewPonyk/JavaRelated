import { AlertCircle, MapPin, Navigation, Play, RefreshCw, Route, Shield, Truck, User } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import type React from "react";
import { MapContainer, Marker, Polygon, Popup, TileLayer } from "react-leaflet";

import { api } from "../api/client";
import type { Driver, Geofence, GeofenceEvent, RoutePrediction, Telemetry, Trip, Vehicle } from "../types/domain";

const DEFAULT_CENTER: [number, number] = [50.4501, 30.5234];
const DEFAULT_POLYGON: GeoJSON.Polygon = {
  type: "Polygon",
  coordinates: [
    [
      [30.48, 50.43],
      [30.58, 50.43],
      [30.58, 50.49],
      [30.48, 50.49],
      [30.48, 50.43]
    ]
  ]
};

export function FleetDashboard() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [geofences, setGeofences] = useState<Geofence[]>([]);
  const [events, setEvents] = useState<GeofenceEvent[]>([]);
  const [trips, setTrips] = useState<Trip[]>([]);
  const [predictions, setPredictions] = useState<RoutePrediction[]>([]);
  const [playback, setPlayback] = useState<Telemetry[]>([]);
  const [selectedTripId, setSelectedTripId] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [driverForm, setDriverForm] = useState({ name: "", phone: "", email: "" });
  const [vehicleForm, setVehicleForm] = useState({ name: "", license_plate: "", driver_id: "" });
  const [telemetryForm, setTelemetryForm] = useState({
    vehicle_id: "",
    latitude: "50.4501",
    longitude: "30.5234",
    speed_kph: "42",
    heading_degrees: "90"
  });
  const [geofenceForm, setGeofenceForm] = useState({
    name: "Kyiv central operating zone",
    description: "Default city-center service polygon",
    boundary: JSON.stringify(DEFAULT_POLYGON, null, 2)
  });
  const [tripForm, setTripForm] = useState({
    vehicle_id: "",
    name: "",
    origin_latitude: "50.4501",
    origin_longitude: "30.5234",
    destination_latitude: "50.4017",
    destination_longitude: "30.2525"
  });
  const [routeForm, setRouteForm] = useState({
    vehicle_id: "",
    destination_latitude: "50.4017",
    destination_longitude: "30.2525"
  });

  async function loadAll() {
    setIsLoading(true);
    setError(null);
    try {
      const [vehicleData, driverData, geofenceData, eventData, tripData, predictionData] = await Promise.all([
        api.listVehicles(),
        api.listDrivers(),
        api.listGeofences(),
        api.listGeofenceEvents(),
        api.listTrips(),
        api.listPredictions()
      ]);
      setVehicles(vehicleData);
      setDrivers(driverData);
      setGeofences(geofenceData);
      setEvents(eventData);
      setTrips(tripData);
      setPredictions(predictionData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load fleet data");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadAll();
  }, []);

  const visibleVehicles = useMemo(
    () => vehicles.filter((vehicle) => vehicle.latest_latitude !== null && vehicle.latest_longitude !== null),
    [vehicles]
  );

  async function submitDriver(event: FormEvent) {
    event.preventDefault();
    await runAction(async () => {
      await api.createDriver({
        name: driverForm.name,
        phone: driverForm.phone || null,
        email: driverForm.email || null
      });
      setDriverForm({ name: "", phone: "", email: "" });
      setNotice("Driver created");
    });
  }

  async function submitVehicle(event: FormEvent) {
    event.preventDefault();
    await runAction(async () => {
      await api.createVehicle({
        name: vehicleForm.name,
        license_plate: vehicleForm.license_plate,
        status: "idle",
        driver_id: vehicleForm.driver_id || null
      });
      setVehicleForm({ name: "", license_plate: "", driver_id: "" });
      setNotice("Vehicle created");
    });
  }

  async function submitTelemetry(event: FormEvent) {
    event.preventDefault();
    await runAction(async () => {
      await api.ingestTelemetry({
        vehicle_id: telemetryForm.vehicle_id,
        recorded_at: new Date().toISOString(),
        latitude: Number(telemetryForm.latitude),
        longitude: Number(telemetryForm.longitude),
        speed_kph: Number(telemetryForm.speed_kph),
        heading_degrees: Number(telemetryForm.heading_degrees),
        raw_payload: { source: "operator-ui" }
      });
      setNotice("Telemetry ingested");
    });
  }

  async function submitGeofence(event: FormEvent) {
    event.preventDefault();
    await runAction(async () => {
      await api.createGeofence({
        name: geofenceForm.name,
        description: geofenceForm.description,
        boundary_geojson: JSON.parse(geofenceForm.boundary) as GeoJSON.Polygon
      });
      setNotice("Geofence created");
    });
  }

  async function submitTrip(event: FormEvent) {
    event.preventDefault();
    await runAction(async () => {
      await api.createTrip({
        vehicle_id: tripForm.vehicle_id,
        name: tripForm.name,
        origin_latitude: Number(tripForm.origin_latitude),
        origin_longitude: Number(tripForm.origin_longitude),
        destination_latitude: Number(tripForm.destination_latitude),
        destination_longitude: Number(tripForm.destination_longitude),
        status: "planned"
      });
      setNotice("Trip created");
    });
  }

  async function submitRoute(event: FormEvent) {
    event.preventDefault();
    await runAction(async () => {
      await api.predictRoute({
        vehicle_id: routeForm.vehicle_id,
        destination_latitude: Number(routeForm.destination_latitude),
        destination_longitude: Number(routeForm.destination_longitude)
      });
      setNotice("ETA prediction created");
    });
  }

  async function loadPlayback(tripId: string) {
    setSelectedTripId(tripId);
    await runAction(async () => {
      setPlayback(tripId ? await api.tripPlayback(tripId) : []);
    }, false);
  }

  async function runAction(action: () => Promise<void>, refresh = true) {
    setError(null);
    setNotice(null);
    try {
      await action();
      if (refresh) {
        await loadAll();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action failed");
    }
  }

  return (
    <main className="app-shell">
      <section className="sidebar" aria-label="Fleet operations">
        <div className="panel-header">
          <div>
            <h1>Fleet Management</h1>
            <p>Live tracking, geofences, trip playback, and ETA prediction.</p>
          </div>
          <button type="button" onClick={loadAll} disabled={isLoading} aria-label="Refresh data">
            <RefreshCw size={18} />
          </button>
        </div>

        {isLoading && <StateRow icon={<RefreshCw size={18} />} text="Loading fleet data..." />}
        {error && <StateRow icon={<AlertCircle size={18} />} text={error} tone="error" />}
        {notice && <StateRow icon={<Shield size={18} />} text={notice} tone="success" />}

        <div className="metrics">
          <Metric label="Vehicles" value={vehicles.length} />
          <Metric label="Active" value={visibleVehicles.length} />
          <Metric label="Geofences" value={geofences.length} />
          <Metric label="Trips" value={trips.length} />
        </div>

        <div className="forms-grid">
          <form onSubmit={submitDriver}>
            <h2><User size={18} /> Driver</h2>
            <input required placeholder="Name" value={driverForm.name} onChange={(e) => setDriverForm({ ...driverForm, name: e.target.value })} />
            <input placeholder="Phone" value={driverForm.phone} onChange={(e) => setDriverForm({ ...driverForm, phone: e.target.value })} />
            <input type="email" placeholder="Email" value={driverForm.email} onChange={(e) => setDriverForm({ ...driverForm, email: e.target.value })} />
            <button className="text-button" type="submit">Create driver</button>
          </form>

          <form onSubmit={submitVehicle}>
            <h2><Truck size={18} /> Vehicle</h2>
            <input required placeholder="Name" value={vehicleForm.name} onChange={(e) => setVehicleForm({ ...vehicleForm, name: e.target.value })} />
            <input required placeholder="License plate" value={vehicleForm.license_plate} onChange={(e) => setVehicleForm({ ...vehicleForm, license_plate: e.target.value })} />
            <select value={vehicleForm.driver_id} onChange={(e) => setVehicleForm({ ...vehicleForm, driver_id: e.target.value })}>
              <option value="">Unassigned</option>
              {drivers.map((driver) => <option key={driver.id} value={driver.id}>{driver.name}</option>)}
            </select>
            <button className="text-button" type="submit">Create vehicle</button>
          </form>

          <form onSubmit={submitTelemetry}>
            <h2><MapPin size={18} /> Telemetry</h2>
            <VehicleSelect vehicles={vehicles} value={telemetryForm.vehicle_id} onChange={(vehicle_id) => setTelemetryForm({ ...telemetryForm, vehicle_id })} />
            <div className="pair">
              <input required type="number" step="0.000001" placeholder="Latitude" value={telemetryForm.latitude} onChange={(e) => setTelemetryForm({ ...telemetryForm, latitude: e.target.value })} />
              <input required type="number" step="0.000001" placeholder="Longitude" value={telemetryForm.longitude} onChange={(e) => setTelemetryForm({ ...telemetryForm, longitude: e.target.value })} />
            </div>
            <div className="pair">
              <input type="number" min="0" placeholder="Speed" value={telemetryForm.speed_kph} onChange={(e) => setTelemetryForm({ ...telemetryForm, speed_kph: e.target.value })} />
              <input type="number" min="0" max="359" placeholder="Heading" value={telemetryForm.heading_degrees} onChange={(e) => setTelemetryForm({ ...telemetryForm, heading_degrees: e.target.value })} />
            </div>
            <button className="text-button" type="submit">Ingest location</button>
          </form>

          <form onSubmit={submitGeofence}>
            <h2><Shield size={18} /> Geofence</h2>
            <input required placeholder="Name" value={geofenceForm.name} onChange={(e) => setGeofenceForm({ ...geofenceForm, name: e.target.value })} />
            <input placeholder="Description" value={geofenceForm.description} onChange={(e) => setGeofenceForm({ ...geofenceForm, description: e.target.value })} />
            <textarea required value={geofenceForm.boundary} onChange={(e) => setGeofenceForm({ ...geofenceForm, boundary: e.target.value })} />
            <button className="text-button" type="submit">Create geofence</button>
          </form>

          <form onSubmit={submitTrip}>
            <h2><Route size={18} /> Trip</h2>
            <VehicleSelect vehicles={vehicles} value={tripForm.vehicle_id} onChange={(vehicle_id) => setTripForm({ ...tripForm, vehicle_id })} />
            <input required placeholder="Trip name" value={tripForm.name} onChange={(e) => setTripForm({ ...tripForm, name: e.target.value })} />
            <div className="pair">
              <input required type="number" step="0.000001" value={tripForm.origin_latitude} onChange={(e) => setTripForm({ ...tripForm, origin_latitude: e.target.value })} />
              <input required type="number" step="0.000001" value={tripForm.origin_longitude} onChange={(e) => setTripForm({ ...tripForm, origin_longitude: e.target.value })} />
            </div>
            <div className="pair">
              <input required type="number" step="0.000001" value={tripForm.destination_latitude} onChange={(e) => setTripForm({ ...tripForm, destination_latitude: e.target.value })} />
              <input required type="number" step="0.000001" value={tripForm.destination_longitude} onChange={(e) => setTripForm({ ...tripForm, destination_longitude: e.target.value })} />
            </div>
            <button className="text-button" type="submit">Create trip</button>
          </form>

          <form onSubmit={submitRoute}>
            <h2><Navigation size={18} /> ETA</h2>
            <VehicleSelect vehicles={vehicles} value={routeForm.vehicle_id} onChange={(vehicle_id) => setRouteForm({ ...routeForm, vehicle_id })} />
            <div className="pair">
              <input required type="number" step="0.000001" value={routeForm.destination_latitude} onChange={(e) => setRouteForm({ ...routeForm, destination_latitude: e.target.value })} />
              <input required type="number" step="0.000001" value={routeForm.destination_longitude} onChange={(e) => setRouteForm({ ...routeForm, destination_longitude: e.target.value })} />
            </div>
            <button className="text-button" type="submit">Predict ETA</button>
          </form>
        </div>
      </section>

      <section className="map-region" aria-label="Fleet map">
        <MapContainer center={DEFAULT_CENTER} zoom={11} className="map">
          <TileLayer attribution="&copy; OpenStreetMap contributors" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
          {geofences.map((geofence) => (
            <Polygon
              key={geofence.id}
              positions={geofence.boundary_geojson.coordinates[0].map(([lng, lat]) => [lat, lng])}
              pathOptions={{ color: "#1d4f73", fillOpacity: 0.08 }}
            />
          ))}
          {visibleVehicles.map((vehicle) => (
            <Marker key={vehicle.id} position={[vehicle.latest_latitude as number, vehicle.latest_longitude as number]}>
              <Popup>
                <strong>{vehicle.name}</strong>
                <br />
                {vehicle.license_plate}
              </Popup>
            </Marker>
          ))}
          {playback.map((point) => (
            <Marker key={`playback-${point.id}`} position={[point.latitude, point.longitude]}>
              <Popup>{new Date(point.recorded_at).toLocaleString()}</Popup>
            </Marker>
          ))}
        </MapContainer>

        <aside className="data-panel">
          <section>
            <h2><Truck size={18} /> Vehicles</h2>
            <ItemList items={vehicles.map((vehicle) => `${vehicle.name} · ${vehicle.license_plate} · ${vehicle.status}`)} empty="No vehicles yet" />
          </section>
          <section>
            <h2><Shield size={18} /> Geofence Events</h2>
            <ItemList items={events.slice(0, 6).map((event) => `${event.event_type} · ${event.vehicle_id.slice(0, 8)} · ${new Date(event.recorded_at).toLocaleTimeString()}`)} empty="No events yet" />
          </section>
          <section>
            <h2><Play size={18} /> Playback</h2>
            <select value={selectedTripId} onChange={(event) => void loadPlayback(event.target.value)}>
              <option value="">Select trip</option>
              {trips.map((trip) => <option key={trip.id} value={trip.id}>{trip.name}</option>)}
            </select>
            <ItemList items={playback.map((point) => `${point.latitude.toFixed(4)}, ${point.longitude.toFixed(4)}`)} empty="No playback loaded" />
          </section>
          <section>
            <h2><Navigation size={18} /> Predictions</h2>
            <ItemList items={predictions.slice(0, 6).map((prediction) => `${prediction.distance_km.toFixed(1)} km · ${Math.round(prediction.eta_seconds / 60)} min · ${(prediction.confidence * 100).toFixed(0)}%`)} empty="No predictions yet" />
          </section>
        </aside>
      </section>
    </main>
  );
}

function VehicleSelect({ vehicles, value, onChange }: { vehicles: Vehicle[]; value: string; onChange: (value: string) => void }) {
  return (
    <select required value={value} onChange={(event) => onChange(event.target.value)}>
      <option value="">Select vehicle</option>
      {vehicles.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.name}</option>)}
    </select>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function StateRow({ icon, text, tone }: { icon: React.ReactNode; text: string; tone?: "error" | "success" }) {
  return <div className={`state-row ${tone ?? ""}`}>{icon}<span>{text}</span></div>;
}

function ItemList({ items, empty }: { items: string[]; empty: string }) {
  if (items.length === 0) {
    return <p className="empty">{empty}</p>;
  }
  return <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>;
}
