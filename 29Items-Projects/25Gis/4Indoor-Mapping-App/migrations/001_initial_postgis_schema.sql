CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE venues (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  venue_type text NOT NULL CHECK (venue_type IN ('mall', 'airport')),
  timezone text NOT NULL,
  boundary geography(polygon, 4326),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE floors (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
  level integer NOT NULL,
  name text NOT NULL,
  floor_plan geography(polygon, 4326),
  mapbox_layer_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (venue_id, level)
);

CREATE TABLE pois (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
  floor_id uuid NOT NULL REFERENCES floors(id) ON DELETE CASCADE,
  name text NOT NULL,
  category text NOT NULL,
  description text,
  location geography(point, 4326) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE route_nodes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
  floor_id uuid NOT NULL REFERENCES floors(id) ON DELETE CASCADE,
  node_type text NOT NULL DEFAULT 'walkway',
  location geography(point, 4326) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE route_edges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
  from_node_id uuid NOT NULL REFERENCES route_nodes(id) ON DELETE CASCADE,
  to_node_id uuid NOT NULL REFERENCES route_nodes(id) ON DELETE CASCADE,
  travel_cost double precision NOT NULL,
  is_accessible boolean NOT NULL DEFAULT true,
  geometry geography(linestring, 4326) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE beacon_anchors (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
  floor_id uuid NOT NULL REFERENCES floors(id) ON DELETE CASCADE,
  provider text NOT NULL,
  external_id text NOT NULL,
  location geography(point, 4326) NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, external_id)
);

CREATE TABLE wifi_fingerprints (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
  floor_id uuid NOT NULL REFERENCES floors(id) ON DELETE CASCADE,
  location geography(point, 4326) NOT NULL,
  scan jsonb NOT NULL,
  collected_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE positioning_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
  floor_id uuid REFERENCES floors(id) ON DELETE SET NULL,
  user_hash text,
  estimated_location geography(point, 4326),
  confidence double precision CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  raw_signal_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  observed_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE heatmap_cells (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
  floor_id uuid NOT NULL REFERENCES floors(id) ON DELETE CASCADE,
  cell geography(polygon, 4326) NOT NULL,
  density_score double precision NOT NULL CHECK (density_score >= 0),
  calculated_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX venues_boundary_gix ON venues USING gist (boundary);
CREATE INDEX floors_floor_plan_gix ON floors USING gist (floor_plan);
CREATE INDEX floors_venue_level_idx ON floors (venue_id, level);
CREATE INDEX pois_location_gix ON pois USING gist (location);
CREATE INDEX pois_search_idx ON pois (venue_id, floor_id, category);
CREATE INDEX pois_name_trgm_idx ON pois USING gin (name gin_trgm_ops);
CREATE INDEX pois_description_trgm_idx ON pois USING gin (description gin_trgm_ops);
CREATE INDEX route_nodes_location_gix ON route_nodes USING gist (location);
CREATE INDEX route_nodes_venue_floor_idx ON route_nodes (venue_id, floor_id);
CREATE INDEX route_edges_geometry_gix ON route_edges USING gist (geometry);
CREATE INDEX route_edges_venue_from_idx ON route_edges (venue_id, from_node_id);
CREATE INDEX route_edges_venue_to_idx ON route_edges (venue_id, to_node_id);
CREATE INDEX beacon_anchors_location_gix ON beacon_anchors USING gist (location);
CREATE INDEX beacon_anchors_venue_floor_idx ON beacon_anchors (venue_id, floor_id);
CREATE INDEX wifi_fingerprints_location_gix ON wifi_fingerprints USING gist (location);
CREATE INDEX wifi_fingerprints_venue_floor_idx ON wifi_fingerprints (venue_id, floor_id);
CREATE INDEX positioning_events_observed_idx ON positioning_events (venue_id, observed_at DESC);
CREATE INDEX positioning_events_floor_observed_idx ON positioning_events (floor_id, observed_at DESC);
CREATE INDEX heatmap_cells_cell_gix ON heatmap_cells USING gist (cell);
CREATE INDEX heatmap_cells_latest_idx ON heatmap_cells (venue_id, floor_id, calculated_at DESC);
