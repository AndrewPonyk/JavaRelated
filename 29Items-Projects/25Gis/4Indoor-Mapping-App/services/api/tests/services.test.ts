import type { Database } from '../src/db/pool';
import { BeaconAnchorService } from '../src/services/beacon.service';
import { FloorService } from '../src/services/floor.service';
import { HeatmapCellService } from '../src/services/heatmap.service';
import { PoiService } from '../src/services/poi.service';
import { PositioningEventService } from '../src/services/positioning.service';
import { RouteService } from '../src/services/route.service';
import { VenueService } from '../src/services/venue.service';
import { WifiFingerprintService } from '../src/services/wifi.service';
import type { PolygonGeoJson } from '../src/schemas/common.schema';

const ids = {
  venue: '11111111-1111-1111-1111-111111111111',
  floor: '22222222-2222-2222-2222-222222222222',
  nodeA: '33333333-3333-3333-3333-333333333333',
  nodeB: '44444444-4444-4444-4444-444444444444',
  nodeC: '55555555-5555-5555-5555-555555555555',
  edgeA: '66666666-6666-6666-6666-666666666666',
  edgeB: '77777777-7777-7777-7777-777777777777',
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

describe('entity services', () => {
  it('lists POIs with filters, search text, spatial ranking, and a limit', async () => {
    const db = createDb([
      [
        {
          id: '88888888-8888-8888-8888-888888888888',
          venue_id: ids.venue,
          floor_id: ids.floor,
          name: 'Gate A1',
          category: 'gate',
          description: null,
          latitude: 50.45,
          longitude: 30.52,
          distance_meters: 10.5,
          created_at: '2026-01-01T00:00:00.000Z',
          updated_at: '2026-01-01T00:00:00.000Z',
        },
      ],
    ]);

    const result = await new PoiService(db).list({
      venueId: ids.venue,
      floorId: ids.floor,
      category: 'gate',
      query: 'A1',
      latitude: 50.4501,
      longitude: 30.5201,
      limit: 20,
    });

    expect(result[0]).toMatchObject({
      name: 'Gate A1',
      distanceMeters: 10.5,
    });
    expect(jest.mocked(db.query).mock.calls[0][0]).toContain('ST_Distance');
    expect(jest.mocked(db.query).mock.calls[0][1]).toContain(20);
  });

  it('creates and updates venues, floors, beacons, WiFi fingerprints, and heatmap cells', async () => {
    const now = '2026-01-01T00:00:00.000Z';
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
    const db = createDb([
      [
        {
          id: ids.venue,
          name: 'Central Mall',
          venue_type: 'mall',
          timezone: 'Europe/Kiev',
          boundary: polygon,
          created_at: now,
          updated_at: now,
        },
      ],
      [
        {
          id: ids.floor,
          venue_id: ids.venue,
          level: 1,
          name: 'Level 1',
          floor_plan: polygon,
          mapbox_layer_id: 'floor-1',
          created_at: now,
          updated_at: now,
        },
      ],
      [
        {
          id: '99999999-9999-9999-9999-999999999999',
          venue_id: ids.venue,
          floor_id: ids.floor,
          provider: 'estimote',
          external_id: 'beacon-a',
          latitude: 50.45,
          longitude: 30.52,
          metadata: { zone: 'north' },
          created_at: now,
        },
      ],
      [
        {
          id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
          venue_id: ids.venue,
          floor_id: ids.floor,
          latitude: 50.45,
          longitude: 30.52,
          scan: [{ bssid: 'ap-1', rssi: -55 }],
          collected_at: now,
          created_at: now,
        },
      ],
      [
        {
          id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
          venue_id: ids.venue,
          floor_id: ids.floor,
          cell: polygon,
          density_score: 2.4,
          calculated_at: now,
          created_at: now,
        },
      ],
    ]);

    await expect(
      new VenueService(db).create({
        name: 'Central Mall',
        venueType: 'mall',
        timezone: 'Europe/Kiev',
        boundary: polygon,
      }),
    ).resolves.toMatchObject({ id: ids.venue, venueType: 'mall' });

    await expect(
      new FloorService(db).create({
        venueId: ids.venue,
        level: 1,
        name: 'Level 1',
        floorPlan: polygon,
        mapboxLayerId: 'floor-1',
      }),
    ).resolves.toMatchObject({ level: 1, mapboxLayerId: 'floor-1' });

    await expect(
      new BeaconAnchorService(db).create({
        venueId: ids.venue,
        floorId: ids.floor,
        provider: 'estimote',
        externalId: 'beacon-a',
        latitude: 50.45,
        longitude: 30.52,
        metadata: { zone: 'north' },
      }),
    ).resolves.toMatchObject({ externalId: 'beacon-a' });

    await expect(
      new WifiFingerprintService(db).create({
        venueId: ids.venue,
        floorId: ids.floor,
        latitude: 50.45,
        longitude: 30.52,
        scan: [{ bssid: 'ap-1', rssi: -55 }],
        collectedAt: now,
      }),
    ).resolves.toMatchObject({ scan: [{ bssid: 'ap-1', rssi: -55 }] });

    await expect(
      new HeatmapCellService(db).create({
        venueId: ids.venue,
        floorId: ids.floor,
        cell: polygon,
        densityScore: 2.4,
        calculatedAt: now,
      }),
    ).resolves.toMatchObject({ densityScore: 2.4 });
  });

  it('calculates the shortest route with accessible edge filtering', async () => {
    const now = '2026-01-01T00:00:00.000Z';
    const geometry = {
      type: 'LineString',
      coordinates: [
        [30.52, 50.45],
        [30.53, 50.45],
      ],
    };
    const db = createDb([
      [
        {
          id: ids.edgeA,
          venue_id: ids.venue,
          from_node_id: ids.nodeA,
          to_node_id: ids.nodeB,
          travel_cost: 5,
          is_accessible: true,
          geometry,
          created_at: now,
        },
        {
          id: ids.edgeB,
          venue_id: ids.venue,
          from_node_id: ids.nodeB,
          to_node_id: ids.nodeC,
          travel_cost: 4,
          is_accessible: true,
          geometry,
          created_at: now,
        },
      ],
    ]);

    const directions = await new RouteService(db).getDirections({
      venueId: ids.venue,
      fromNodeId: ids.nodeA,
      toNodeId: ids.nodeC,
      accessibleOnly: true,
    });

    expect(directions).toMatchObject({
      totalCost: 9,
      nodeIds: [ids.nodeA, ids.nodeB, ids.nodeC],
    });
    expect(jest.mocked(db.query).mock.calls[0][1]).toEqual([ids.venue, true]);
  });

  it('stores a positioning ingest event using the closest WiFi fingerprint', async () => {
    const now = '2026-01-01T00:00:00.000Z';
    const db = createDb([
      [
        {
          id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
          venue_id: ids.venue,
          floor_id: ids.floor,
          latitude: 50.45,
          longitude: 30.52,
          scan: [{ bssid: 'ap-1', rssi: -55 }],
          collected_at: now,
          created_at: now,
        },
      ],
      [
        {
          id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          venue_id: ids.venue,
          floor_id: ids.floor,
          user_hash: 'user-1',
          latitude: 50.45,
          longitude: 30.52,
          confidence: 0.95,
          raw_signal_summary: {
            positioningSource: 'wifi_fingerprint',
            fingerprintId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
          },
          observed_at: now,
          created_at: now,
        },
      ],
    ]);

    const event = await new PositioningEventService(db).ingest({
      venueId: ids.venue,
      userHash: 'user-1',
      wifiReadings: [{ bssid: 'ap-1', rssi: -55 }],
      beaconReadings: [],
      observedAt: now,
    });

    expect(event).toMatchObject({
      floorId: ids.floor,
      latitude: 50.45,
      confidence: 0.95,
    });
    expect(jest.mocked(db.query)).toHaveBeenCalledTimes(2);
  });
});
