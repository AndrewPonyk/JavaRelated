import type { Database } from '../src/db/pool';
import type { LineStringGeoJson, PolygonGeoJson } from '../src/schemas/common.schema';
import { BeaconAnchorService } from '../src/services/beacon.service';
import { FloorService } from '../src/services/floor.service';
import { HeatmapCellService } from '../src/services/heatmap.service';
import { PoiService } from '../src/services/poi.service';
import { PositioningEventService } from '../src/services/positioning.service';
import { RouteService } from '../src/services/route.service';
import { VenueService } from '../src/services/venue.service';
import { WifiFingerprintService } from '../src/services/wifi.service';

const now = '2026-01-01T00:00:00.000Z';
const venueId = '11111111-1111-1111-1111-111111111111';
const floorId = '22222222-2222-2222-2222-222222222222';
const id = '33333333-3333-3333-3333-333333333333';

const polygon: PolygonGeoJson = {
  type: 'Polygon',
  coordinates: [
    [
      [30.52, 50.45],
      [30.53, 50.45],
      [30.53, 50.46],
      [30.52, 50.45],
    ],
  ],
};

const lineString: LineStringGeoJson = {
  type: 'LineString',
  coordinates: [
    [30.52, 50.45],
    [30.53, 50.45],
  ],
};

function createDb(rows: Record<string, unknown>[][], rowCounts: number[] = []): Database {
  const query = jest.fn();
  for (const [index, itemRows] of rows.entries()) {
    query.mockResolvedValueOnce({
      rows: itemRows,
      rowCount: rowCounts[index] ?? itemRows.length,
    });
  }
  return { query } as unknown as Database;
}

const venueRow = {
  id: venueId,
  name: 'Central Mall',
  venue_type: 'mall',
  timezone: 'Europe/Kiev',
  boundary: polygon,
  created_at: now,
  updated_at: now,
};

const floorRow = {
  id: floorId,
  venue_id: venueId,
  level: 1,
  name: 'Level 1',
  floor_plan: polygon,
  mapbox_layer_id: 'floor-1',
  created_at: now,
  updated_at: now,
};

const poiRow = {
  id,
  venue_id: venueId,
  floor_id: floorId,
  name: 'Cafe',
  category: 'food',
  description: 'Coffee',
  latitude: 50.45,
  longitude: 30.52,
  distance_meters: null,
  created_at: now,
  updated_at: now,
};

const routeNodeRow = {
  id,
  venue_id: venueId,
  floor_id: floorId,
  node_type: 'walkway',
  latitude: 50.45,
  longitude: 30.52,
  created_at: now,
};

const routeEdgeRow = {
  id,
  venue_id: venueId,
  from_node_id: '44444444-4444-4444-4444-444444444444',
  to_node_id: '55555555-5555-5555-5555-555555555555',
  travel_cost: 3,
  is_accessible: true,
  geometry: lineString,
  created_at: now,
};

const beaconRow = {
  id,
  venue_id: venueId,
  floor_id: floorId,
  provider: 'estimote',
  external_id: 'beacon-a',
  latitude: 50.45,
  longitude: 30.52,
  metadata: {},
  created_at: now,
};

const wifiRow = {
  id,
  venue_id: venueId,
  floor_id: floorId,
  latitude: 50.45,
  longitude: 30.52,
  scan: [{ bssid: 'ap-1', rssi: -55 }],
  collected_at: now,
  created_at: now,
};

const positioningRow = {
  id,
  venue_id: venueId,
  floor_id: floorId,
  user_hash: 'user-1',
  latitude: 50.45,
  longitude: 30.52,
  confidence: 0.8,
  raw_signal_summary: {},
  observed_at: now,
  created_at: now,
};

const heatmapRow = {
  id,
  venue_id: venueId,
  floor_id: floorId,
  cell: polygon,
  density_score: 1.5,
  calculated_at: now,
  created_at: now,
};

describe('CRUD service branches', () => {
  it('supports venue list, get, update, and delete paths', async () => {
    const db = createDb([[venueRow], [venueRow], [venueRow], [venueRow], []], [1, 1, 1, 1, 1]);
    const service = new VenueService(db);

    await expect(
      service.list({ query: 'mall', venueType: 'mall', limit: 10 }),
    ).resolves.toHaveLength(1);
    await expect(service.getById(venueId)).resolves.toMatchObject({ name: 'Central Mall' });
    await expect(service.update(venueId, { name: 'Central Mall Updated' })).resolves.toMatchObject({
      venueType: 'mall',
    });
    await expect(service.delete(venueId)).resolves.toBe(true);
  });

  it('supports floor list, get, update, and delete paths', async () => {
    const db = createDb([[floorRow], [floorRow], [floorRow], [floorRow], []], [1, 1, 1, 1, 1]);
    const service = new FloorService(db);

    await expect(service.list({ venueId, limit: 10 })).resolves.toHaveLength(1);
    await expect(service.getById(floorId)).resolves.toMatchObject({ level: 1 });
    await expect(service.update(floorId, { name: 'Main Floor' })).resolves.toMatchObject({
      mapboxLayerId: 'floor-1',
    });
    await expect(service.delete(floorId)).resolves.toBe(true);
  });

  it('supports POI get, create, update, and delete paths', async () => {
    const db = createDb([[poiRow], [poiRow], [poiRow], [poiRow], []], [1, 1, 1, 1, 1]);
    const service = new PoiService(db);

    await expect(service.getById(id)).resolves.toMatchObject({ name: 'Cafe' });
    await expect(
      service.create({
        venueId,
        floorId,
        name: 'Cafe',
        category: 'food',
        description: 'Coffee',
        latitude: 50.45,
        longitude: 30.52,
      }),
    ).resolves.toMatchObject({ category: 'food' });
    await expect(service.update(id, { name: 'Cafe 2' })).resolves.toMatchObject({ floorId });
    await expect(service.delete(id)).resolves.toBe(true);
  });

  it('supports route node and edge CRUD paths', async () => {
    const db = createDb(
      [
        [routeNodeRow],
        [routeNodeRow],
        [routeNodeRow],
        [routeNodeRow],
        [routeNodeRow],
        [],
        [routeEdgeRow],
        [routeEdgeRow],
        [routeEdgeRow],
        [routeEdgeRow],
        [routeEdgeRow],
        [],
      ],
      [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
    );
    const service = new RouteService(db);

    await expect(
      service.listNodes({ venueId, floorId, nodeType: 'walkway', limit: 10 }),
    ).resolves.toHaveLength(1);
    await expect(service.getNodeById(id)).resolves.toMatchObject({ nodeType: 'walkway' });
    await expect(
      service.createNode({
        venueId,
        floorId,
        nodeType: 'walkway',
        latitude: 50.45,
        longitude: 30.52,
      }),
    ).resolves.toMatchObject({ floorId });
    await expect(service.updateNode(id, { latitude: 50.46 })).resolves.toMatchObject({
      latitude: 50.45,
    });
    await expect(service.deleteNode(id)).resolves.toBe(true);

    await expect(
      service.listEdges({ venueId, accessibleOnly: true, limit: 10 }),
    ).resolves.toHaveLength(1);
    await expect(service.getEdgeById(id)).resolves.toMatchObject({ travelCost: 3 });
    await expect(
      service.createEdge({
        venueId,
        fromNodeId: routeEdgeRow.from_node_id,
        toNodeId: routeEdgeRow.to_node_id,
        travelCost: 3,
        isAccessible: true,
        geometry: lineString,
      }),
    ).resolves.toMatchObject({ isAccessible: true });
    await expect(service.updateEdge(id, { travelCost: 4 })).resolves.toMatchObject({
      travelCost: 3,
    });
    await expect(service.deleteEdge(id)).resolves.toBe(true);
  });

  it('supports beacon, WiFi, positioning, and heatmap CRUD paths', async () => {
    const db = createDb(
      [
        [beaconRow],
        [beaconRow],
        [beaconRow],
        [beaconRow],
        [],
        [wifiRow],
        [wifiRow],
        [wifiRow],
        [wifiRow],
        [],
        [positioningRow],
        [positioningRow],
        [positioningRow],
        [positioningRow],
        [positioningRow],
        [],
        [heatmapRow],
        [heatmapRow],
        [heatmapRow],
        [heatmapRow],
        [heatmapRow],
        [],
      ],
      Array(22).fill(1),
    );

    const beacon = new BeaconAnchorService(db);
    await expect(
      beacon.list({ venueId, floorId, provider: 'estimote', limit: 10 }),
    ).resolves.toHaveLength(1);
    await expect(beacon.getById(id)).resolves.toMatchObject({ externalId: 'beacon-a' });
    await expect(beacon.update(id, { metadata: { zone: 'south' } })).resolves.toMatchObject({
      provider: 'estimote',
    });
    await expect(beacon.delete(id)).resolves.toBe(true);

    const wifi = new WifiFingerprintService(db);
    await expect(wifi.list({ venueId, floorId, limit: 10 })).resolves.toHaveLength(1);
    await expect(wifi.getById(id)).resolves.toMatchObject({ floorId });
    await expect(wifi.update(id, { latitude: 50.46 })).resolves.toMatchObject({
      scan: wifiRow.scan,
    });
    await expect(wifi.delete(id)).resolves.toBe(true);

    const positioning = new PositioningEventService(db);
    await expect(
      positioning.list({ venueId, floorId, userHash: 'user-1', limit: 10 }),
    ).resolves.toHaveLength(1);
    await expect(positioning.getById(id)).resolves.toMatchObject({ confidence: 0.8 });
    await expect(
      positioning.create({
        venueId,
        floorId,
        userHash: 'user-1',
        latitude: 50.45,
        longitude: 30.52,
        confidence: 0.8,
        rawSignalSummary: {},
        observedAt: now,
      }),
    ).resolves.toMatchObject({ userHash: 'user-1' });
    await expect(positioning.update(id, { confidence: 0.7 })).resolves.toMatchObject({ venueId });
    await expect(positioning.delete(id)).resolves.toBe(true);

    const heatmap = new HeatmapCellService(db);
    await expect(
      heatmap.list({ venueId, floorId, latestOnly: true, limit: 10 }),
    ).resolves.toHaveLength(1);
    await expect(heatmap.getById(id)).resolves.toMatchObject({ densityScore: 1.5 });
    await expect(heatmap.update(id, { densityScore: 2 })).resolves.toMatchObject({ floorId });
    await expect(heatmap.delete(id)).resolves.toBe(true);
  });
});
