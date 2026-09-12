export interface Vehicle {
  id: string;
  name: string;
  license_plate: string;
  status: string;
  driver_id: string | null;
  latest_latitude: number | null;
  latest_longitude: number | null;
  latest_speed_kph: number | null;
  latest_heading_degrees: number | null;
  last_seen_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface Driver {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  created_at: string;
  updated_at: string;
}

export interface Geofence {
  id: string;
  name: string;
  description: string | null;
  boundary_geojson: GeoJSON.Polygon;
  created_at: string;
  updated_at: string;
}

export interface Telemetry {
  id: number;
  vehicle_id: string;
  recorded_at: string;
  latitude: number;
  longitude: number;
  speed_kph: number | null;
  heading_degrees: number | null;
  raw_payload: Record<string, unknown>;
}

export interface Trip {
  id: string;
  vehicle_id: string;
  name: string;
  origin_latitude: number;
  origin_longitude: number;
  destination_latitude: number;
  destination_longitude: number;
  status: string;
  started_at: string | null;
  completed_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface RoutePrediction {
  id: string;
  vehicle_id: string;
  origin_latitude: number;
  origin_longitude: number;
  destination_latitude: number;
  destination_longitude: number;
  distance_km: number;
  eta_seconds: number;
  confidence: number;
  model_version: string;
  created_at: string;
}

export interface GeofenceEvent {
  id: string;
  vehicle_id: string;
  geofence_id: string;
  event_type: string;
  latitude: number;
  longitude: number;
  recorded_at: string;
  created_at: string;
}
