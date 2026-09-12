import type {
  Driver,
  Geofence,
  GeofenceEvent,
  RoutePrediction,
  Telemetry,
  Trip,
  Vehicle
} from "../types/domain";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    }
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = (await response.json()) as { error?: { message?: string }; detail?: string };
      message = body.error?.message ?? body.detail ?? message;
    } catch {
      message = response.statusText || message;
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const api = {
  listVehicles: () => request<Vehicle[]>("/api/v1/vehicles"),
  createVehicle: (payload: Partial<Vehicle>) =>
    request<Vehicle>("/api/v1/vehicles", { method: "POST", body: JSON.stringify(payload) }),
  listDrivers: () => request<Driver[]>("/api/v1/drivers"),
  createDriver: (payload: Pick<Driver, "name" | "phone" | "email">) =>
    request<Driver>("/api/v1/drivers", { method: "POST", body: JSON.stringify(payload) }),
  listGeofences: () => request<Geofence[]>("/api/v1/geofences"),
  createGeofence: (payload: { name: string; description?: string; boundary_geojson: GeoJSON.Polygon }) =>
    request<Geofence>("/api/v1/geofences", { method: "POST", body: JSON.stringify(payload) }),
  listGeofenceEvents: () => request<GeofenceEvent[]>("/api/v1/geofences/events"),
  ingestTelemetry: (payload: {
    vehicle_id: string;
    recorded_at: string;
    latitude: number;
    longitude: number;
    speed_kph?: number | null;
    heading_degrees?: number | null;
    raw_payload?: Record<string, unknown>;
  }) => request<Telemetry>("/api/v1/telemetry", { method: "POST", body: JSON.stringify(payload) }),
  listTrips: () => request<Trip[]>("/api/v1/trips"),
  createTrip: (payload: Partial<Trip>) =>
    request<Trip>("/api/v1/trips", { method: "POST", body: JSON.stringify(payload) }),
  tripPlayback: (tripId: string) => request<Telemetry[]>(`/api/v1/trips/${tripId}/playback`),
  predictRoute: (payload: {
    vehicle_id: string;
    destination_latitude: number;
    destination_longitude: number;
  }) => request<RoutePrediction>("/api/v1/routes/predict", { method: "POST", body: JSON.stringify(payload) }),
  listPredictions: () => request<RoutePrediction[]>("/api/v1/routes/predictions")
};
