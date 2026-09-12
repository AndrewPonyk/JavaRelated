import type { Database } from '../db/pool';
import { asJsonObject, asNumber, asString, type DbRow } from '../db/rows';
import type {
  CreateBeaconAnchorInput,
  ListBeaconAnchorsQuery,
  UpdateBeaconAnchorInput,
} from '../schemas/beacon.schema';

export interface BeaconAnchorDto {
  id: string;
  venueId: string;
  floorId: string;
  provider: string;
  externalId: string;
  latitude: number;
  longitude: number;
  metadata: Record<string, unknown>;
  createdAt: string;
}

function mapBeaconAnchorRow(row: DbRow): BeaconAnchorDto {
  return {
    id: asString(row.id),
    venueId: asString(row.venue_id),
    floorId: asString(row.floor_id),
    provider: asString(row.provider),
    externalId: asString(row.external_id),
    latitude: asNumber(row.latitude),
    longitude: asNumber(row.longitude),
    metadata: asJsonObject(row.metadata),
    createdAt: new Date(asString(row.created_at)).toISOString(),
  };
}

const beaconAnchorSelect = `
  SELECT
    id,
    venue_id,
    floor_id,
    provider,
    external_id,
    ST_Y(location::geometry) AS latitude,
    ST_X(location::geometry) AS longitude,
    metadata,
    created_at
  FROM beacon_anchors
`;

export class BeaconAnchorService {
  constructor(private readonly db: Database) {}

  async list(query: ListBeaconAnchorsQuery): Promise<BeaconAnchorDto[]> {
    const values: unknown[] = [];
    const where: string[] = [];

    if (query.venueId) {
      values.push(query.venueId);
      where.push(`venue_id = $${values.length}`);
    }

    if (query.floorId) {
      values.push(query.floorId);
      where.push(`floor_id = $${values.length}`);
    }

    if (query.provider) {
      values.push(query.provider);
      where.push(`provider = $${values.length}`);
    }

    values.push(query.limit);

    const result = await this.db.query(
      `
        ${beaconAnchorSelect}
        ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
        ORDER BY provider ASC, external_id ASC
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapBeaconAnchorRow);
  }

  async getById(id: string): Promise<BeaconAnchorDto | null> {
    const result = await this.db.query(`${beaconAnchorSelect} WHERE id = $1`, [id]);
    return result.rows[0] ? mapBeaconAnchorRow(result.rows[0]) : null;
  }

  async create(input: CreateBeaconAnchorInput): Promise<BeaconAnchorDto> {
    const result = await this.db.query(
      `
        INSERT INTO beacon_anchors (
          venue_id,
          floor_id,
          provider,
          external_id,
          location,
          metadata
        )
        VALUES (
          $1,
          $2,
          $3,
          $4,
          ST_SetSRID(ST_MakePoint($5, $6), 4326)::geography,
          $7
        )
        RETURNING
          id,
          venue_id,
          floor_id,
          provider,
          external_id,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          metadata,
          created_at
      `,
      [
        input.venueId,
        input.floorId,
        input.provider,
        input.externalId,
        input.longitude,
        input.latitude,
        input.metadata,
      ],
    );

    return mapBeaconAnchorRow(result.rows[0]);
  }

  async update(id: string, input: UpdateBeaconAnchorInput): Promise<BeaconAnchorDto | null> {
    const existing = await this.getById(id);
    if (!existing) {
      return null;
    }

    const result = await this.db.query(
      `
        UPDATE beacon_anchors
        SET
          floor_id = $2,
          provider = $3,
          external_id = $4,
          location = ST_SetSRID(ST_MakePoint($5, $6), 4326)::geography,
          metadata = $7
        WHERE id = $1
        RETURNING
          id,
          venue_id,
          floor_id,
          provider,
          external_id,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          metadata,
          created_at
      `,
      [
        id,
        input.floorId ?? existing.floorId,
        input.provider ?? existing.provider,
        input.externalId ?? existing.externalId,
        input.longitude ?? existing.longitude,
        input.latitude ?? existing.latitude,
        input.metadata ?? existing.metadata,
      ],
    );

    return result.rows[0] ? mapBeaconAnchorRow(result.rows[0]) : null;
  }

  async delete(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM beacon_anchors WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }
}
