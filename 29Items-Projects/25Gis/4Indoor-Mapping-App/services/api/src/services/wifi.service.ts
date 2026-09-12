import type { Database } from '../db/pool';
import { asNumber, asString, type DbRow } from '../db/rows';
import type {
  CreateWifiFingerprintInput,
  ListWifiFingerprintsQuery,
  UpdateWifiFingerprintInput,
  WifiReading,
} from '../schemas/wifi.schema';

export interface WifiFingerprintDto {
  id: string;
  venueId: string;
  floorId: string;
  latitude: number;
  longitude: number;
  scan: WifiReading[];
  collectedAt: string;
  createdAt: string;
}

function parseScan(value: unknown): WifiReading[] {
  if (Array.isArray(value)) {
    return value as WifiReading[];
  }

  return [];
}

function mapWifiFingerprintRow(row: DbRow): WifiFingerprintDto {
  return {
    id: asString(row.id),
    venueId: asString(row.venue_id),
    floorId: asString(row.floor_id),
    latitude: asNumber(row.latitude),
    longitude: asNumber(row.longitude),
    scan: parseScan(row.scan),
    collectedAt: new Date(asString(row.collected_at)).toISOString(),
    createdAt: new Date(asString(row.created_at)).toISOString(),
  };
}

const wifiFingerprintSelect = `
  SELECT
    id,
    venue_id,
    floor_id,
    ST_Y(location::geometry) AS latitude,
    ST_X(location::geometry) AS longitude,
    scan,
    collected_at,
    created_at
  FROM wifi_fingerprints
`;

export class WifiFingerprintService {
  constructor(private readonly db: Database) {}

  async list(query: ListWifiFingerprintsQuery): Promise<WifiFingerprintDto[]> {
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

    values.push(query.limit);

    const result = await this.db.query(
      `
        ${wifiFingerprintSelect}
        ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
        ORDER BY collected_at DESC
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapWifiFingerprintRow);
  }

  async getById(id: string): Promise<WifiFingerprintDto | null> {
    const result = await this.db.query(`${wifiFingerprintSelect} WHERE id = $1`, [id]);
    return result.rows[0] ? mapWifiFingerprintRow(result.rows[0]) : null;
  }

  async create(input: CreateWifiFingerprintInput): Promise<WifiFingerprintDto> {
    const collectedAt = input.collectedAt ?? new Date().toISOString();
    const result = await this.db.query(
      `
        INSERT INTO wifi_fingerprints (venue_id, floor_id, location, scan, collected_at)
        VALUES ($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography, $5, $6)
        RETURNING
          id,
          venue_id,
          floor_id,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          scan,
          collected_at,
          created_at
      `,
      [input.venueId, input.floorId, input.longitude, input.latitude, input.scan, collectedAt],
    );

    return mapWifiFingerprintRow(result.rows[0]);
  }

  async update(id: string, input: UpdateWifiFingerprintInput): Promise<WifiFingerprintDto | null> {
    const existing = await this.getById(id);
    if (!existing) {
      return null;
    }

    const result = await this.db.query(
      `
        UPDATE wifi_fingerprints
        SET
          floor_id = $2,
          location = ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
          scan = $5,
          collected_at = $6
        WHERE id = $1
        RETURNING
          id,
          venue_id,
          floor_id,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          scan,
          collected_at,
          created_at
      `,
      [
        id,
        input.floorId ?? existing.floorId,
        input.longitude ?? existing.longitude,
        input.latitude ?? existing.latitude,
        input.scan ?? existing.scan,
        input.collectedAt ?? existing.collectedAt,
      ],
    );

    return result.rows[0] ? mapWifiFingerprintRow(result.rows[0]) : null;
  }

  async delete(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM wifi_fingerprints WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }
}

export { mapWifiFingerprintRow };
