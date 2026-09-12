import type { Database } from '../db/pool';
import { asJsonObject, asNumber, asNullableString, asString, type DbRow } from '../db/rows';
import type {
  CreatePositioningEventInput,
  IngestPositioningEventInput,
  ListPositioningEventsQuery,
  UpdatePositioningEventInput,
} from '../schemas/positioning.schema';
import type { WifiReading } from '../schemas/wifi.schema';
import { mapWifiFingerprintRow, type WifiFingerprintDto } from './wifi.service';

export interface PositioningEventDto {
  id: string;
  venueId: string;
  floorId: string | null;
  userHash: string | null;
  latitude: number | null;
  longitude: number | null;
  confidence: number | null;
  rawSignalSummary: Record<string, unknown>;
  observedAt: string;
  createdAt: string;
}

function mapPositioningEventRow(row: DbRow): PositioningEventDto {
  return {
    id: asString(row.id),
    venueId: asString(row.venue_id),
    floorId: asNullableString(row.floor_id),
    userHash: asNullableString(row.user_hash),
    latitude: row.latitude == null ? null : asNumber(row.latitude),
    longitude: row.longitude == null ? null : asNumber(row.longitude),
    confidence: row.confidence == null ? null : asNumber(row.confidence),
    rawSignalSummary: asJsonObject(row.raw_signal_summary),
    observedAt: new Date(asString(row.observed_at)).toISOString(),
    createdAt: new Date(asString(row.created_at)).toISOString(),
  };
}

const positioningEventSelect = `
  SELECT
    id,
    venue_id,
    floor_id,
    user_hash,
    CASE WHEN estimated_location IS NULL THEN NULL ELSE ST_Y(estimated_location::geometry) END AS latitude,
    CASE WHEN estimated_location IS NULL THEN NULL ELSE ST_X(estimated_location::geometry) END AS longitude,
    confidence,
    raw_signal_summary,
    observed_at,
    created_at
  FROM positioning_events
`;

function wifiDistance(sample: WifiReading[], fingerprint: WifiReading[]): number {
  const fingerprintByBssid = new Map(fingerprint.map((reading) => [reading.bssid, reading.rssi]));
  let distance = 0;
  let matches = 0;

  for (const reading of sample) {
    const expected = fingerprintByBssid.get(reading.bssid);
    if (expected == null) {
      distance += 40;
      continue;
    }

    matches += 1;
    distance += Math.abs(reading.rssi - expected);
  }

  return matches === 0
    ? Number.POSITIVE_INFINITY
    : distance / matches + (sample.length - matches) * 4;
}

export class PositioningEventService {
  constructor(private readonly db: Database) {}

  async list(query: ListPositioningEventsQuery): Promise<PositioningEventDto[]> {
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

    if (query.userHash) {
      values.push(query.userHash);
      where.push(`user_hash = $${values.length}`);
    }

    values.push(query.limit);

    const result = await this.db.query(
      `
        ${positioningEventSelect}
        ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
        ORDER BY observed_at DESC
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapPositioningEventRow);
  }

  async getById(id: string): Promise<PositioningEventDto | null> {
    const result = await this.db.query(`${positioningEventSelect} WHERE id = $1`, [id]);
    return result.rows[0] ? mapPositioningEventRow(result.rows[0]) : null;
  }

  async create(input: CreatePositioningEventInput): Promise<PositioningEventDto> {
    return this.insert({
      venueId: input.venueId,
      floorId: input.floorId,
      userHash: input.userHash,
      latitude: input.latitude,
      longitude: input.longitude,
      confidence: input.confidence,
      rawSignalSummary: input.rawSignalSummary,
      observedAt: input.observedAt,
    });
  }

  async ingest(input: IngestPositioningEventInput): Promise<PositioningEventDto> {
    const candidates = await this.findWifiCandidates(input.venueId, input.floorId);
    let best: WifiFingerprintDto | null = null;
    let bestScore = Number.POSITIVE_INFINITY;

    for (const candidate of candidates) {
      const score = wifiDistance(input.wifiReadings, candidate.scan);
      if (score < bestScore) {
        bestScore = score;
        best = candidate;
      }
    }

    const confidence = best ? Math.max(0.2, Math.min(0.95, 1 - bestScore / 80)) : 0;

    return this.insert({
      venueId: input.venueId,
      floorId: best?.floorId ?? input.floorId,
      userHash: input.userHash,
      latitude: best?.latitude,
      longitude: best?.longitude,
      confidence,
      rawSignalSummary: {
        wifiReadings: input.wifiReadings,
        beaconReadings: input.beaconReadings,
        positioningSource: best ? 'wifi_fingerprint' : 'insufficient_signal',
        fingerprintId: best?.id,
      },
      observedAt: input.observedAt,
    });
  }

  async update(
    id: string,
    input: UpdatePositioningEventInput,
  ): Promise<PositioningEventDto | null> {
    const existing = await this.getById(id);
    if (!existing) {
      return null;
    }

    const result = await this.db.query(
      `
        UPDATE positioning_events
        SET
          floor_id = $2,
          user_hash = $3,
          estimated_location = CASE
            WHEN $4::double precision IS NULL OR $5::double precision IS NULL
            THEN estimated_location
            ELSE ST_SetSRID(ST_MakePoint($5, $4), 4326)::geography
          END,
          confidence = $6,
          raw_signal_summary = $7,
          observed_at = $8
        WHERE id = $1
        RETURNING
          id,
          venue_id,
          floor_id,
          user_hash,
          CASE WHEN estimated_location IS NULL THEN NULL ELSE ST_Y(estimated_location::geometry) END AS latitude,
          CASE WHEN estimated_location IS NULL THEN NULL ELSE ST_X(estimated_location::geometry) END AS longitude,
          confidence,
          raw_signal_summary,
          observed_at,
          created_at
      `,
      [
        id,
        input.floorId ?? existing.floorId,
        input.userHash ?? existing.userHash,
        input.latitude ?? null,
        input.longitude ?? null,
        input.confidence ?? existing.confidence,
        input.rawSignalSummary ?? existing.rawSignalSummary,
        input.observedAt ?? existing.observedAt,
      ],
    );

    return result.rows[0] ? mapPositioningEventRow(result.rows[0]) : null;
  }

  async delete(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM positioning_events WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }

  private async findWifiCandidates(
    venueId: string,
    floorId?: string,
  ): Promise<WifiFingerprintDto[]> {
    const values: unknown[] = [venueId];
    const where = ['venue_id = $1'];

    if (floorId) {
      values.push(floorId);
      where.push(`floor_id = $${values.length}`);
    }

    const result = await this.db.query(
      `
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
        WHERE ${where.join(' AND ')}
        ORDER BY collected_at DESC
        LIMIT 100
      `,
      values,
    );

    return result.rows.map(mapWifiFingerprintRow);
  }

  private async insert(input: {
    venueId: string;
    floorId?: string | null;
    userHash?: string | null;
    latitude?: number | null;
    longitude?: number | null;
    confidence?: number | null;
    rawSignalSummary: Record<string, unknown>;
    observedAt?: string;
  }): Promise<PositioningEventDto> {
    const observedAt = input.observedAt ?? new Date().toISOString();
    const result = await this.db.query(
      `
        INSERT INTO positioning_events (
          venue_id,
          floor_id,
          user_hash,
          estimated_location,
          confidence,
          raw_signal_summary,
          observed_at
        )
        VALUES (
          $1,
          $2,
          $3,
          CASE
            WHEN $4::double precision IS NULL OR $5::double precision IS NULL
            THEN NULL
            ELSE ST_SetSRID(ST_MakePoint($5, $4), 4326)::geography
          END,
          $6,
          $7,
          $8
        )
        RETURNING
          id,
          venue_id,
          floor_id,
          user_hash,
          CASE WHEN estimated_location IS NULL THEN NULL ELSE ST_Y(estimated_location::geometry) END AS latitude,
          CASE WHEN estimated_location IS NULL THEN NULL ELSE ST_X(estimated_location::geometry) END AS longitude,
          confidence,
          raw_signal_summary,
          observed_at,
          created_at
      `,
      [
        input.venueId,
        input.floorId ?? null,
        input.userHash ?? null,
        input.latitude ?? null,
        input.longitude ?? null,
        input.confidence ?? null,
        input.rawSignalSummary,
        observedAt,
      ],
    );

    return mapPositioningEventRow(result.rows[0]);
  }
}
