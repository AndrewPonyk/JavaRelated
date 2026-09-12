import type { Database } from '../db/pool';
import { asNumber, asNullableString, asOptionalJson, asString, type DbRow } from '../db/rows';
import type { CreateFloorInput, ListFloorsQuery, UpdateFloorInput } from '../schemas/floor.schema';

export interface FloorDto {
  id: string;
  venueId: string;
  level: number;
  name: string;
  floorPlan: unknown | null;
  mapboxLayerId: string | null;
  createdAt: string;
  updatedAt: string;
}

function mapFloorRow(row: DbRow): FloorDto {
  return {
    id: asString(row.id),
    venueId: asString(row.venue_id),
    level: asNumber(row.level),
    name: asString(row.name),
    floorPlan: asOptionalJson(row.floor_plan),
    mapboxLayerId: asNullableString(row.mapbox_layer_id),
    createdAt: new Date(asString(row.created_at)).toISOString(),
    updatedAt: new Date(asString(row.updated_at)).toISOString(),
  };
}

const floorSelect = `
  SELECT
    id,
    venue_id,
    level,
    name,
    CASE WHEN floor_plan IS NULL THEN NULL ELSE ST_AsGeoJSON(floor_plan::geometry)::json END AS floor_plan,
    mapbox_layer_id,
    created_at,
    updated_at
  FROM floors
`;

export class FloorService {
  constructor(private readonly db: Database) {}

  async list(query: ListFloorsQuery): Promise<FloorDto[]> {
    const values: unknown[] = [];
    const where: string[] = [];

    if (query.venueId) {
      values.push(query.venueId);
      where.push(`venue_id = $${values.length}`);
    }

    values.push(query.limit);

    const result = await this.db.query(
      `
        ${floorSelect}
        ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
        ORDER BY venue_id ASC, level ASC
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapFloorRow);
  }

  async getById(id: string): Promise<FloorDto | null> {
    const result = await this.db.query(`${floorSelect} WHERE id = $1`, [id]);
    return result.rows[0] ? mapFloorRow(result.rows[0]) : null;
  }

  async create(input: CreateFloorInput): Promise<FloorDto> {
    const result = await this.db.query(
      `
        INSERT INTO floors (venue_id, level, name, floor_plan, mapbox_layer_id)
        VALUES (
          $1,
          $2,
          $3,
          CASE WHEN $4::jsonb IS NULL THEN NULL ELSE ST_GeogFromGeoJSON($4::text) END,
          $5
        )
        RETURNING
          id,
          venue_id,
          level,
          name,
          CASE WHEN floor_plan IS NULL THEN NULL ELSE ST_AsGeoJSON(floor_plan::geometry)::json END AS floor_plan,
          mapbox_layer_id,
          created_at,
          updated_at
      `,
      [
        input.venueId,
        input.level,
        input.name,
        input.floorPlan ? JSON.stringify(input.floorPlan) : null,
        input.mapboxLayerId ?? null,
      ],
    );

    return mapFloorRow(result.rows[0]);
  }

  async update(id: string, input: UpdateFloorInput): Promise<FloorDto | null> {
    const existing = await this.getById(id);
    if (!existing) {
      return null;
    }

    const result = await this.db.query(
      `
        UPDATE floors
        SET
          level = $2,
          name = $3,
          floor_plan = CASE WHEN $4::jsonb IS NULL THEN floor_plan ELSE ST_GeogFromGeoJSON($4::text) END,
          mapbox_layer_id = $5,
          updated_at = now()
        WHERE id = $1
        RETURNING
          id,
          venue_id,
          level,
          name,
          CASE WHEN floor_plan IS NULL THEN NULL ELSE ST_AsGeoJSON(floor_plan::geometry)::json END AS floor_plan,
          mapbox_layer_id,
          created_at,
          updated_at
      `,
      [
        id,
        input.level ?? existing.level,
        input.name ?? existing.name,
        input.floorPlan ? JSON.stringify(input.floorPlan) : null,
        input.mapboxLayerId ?? existing.mapboxLayerId,
      ],
    );

    return result.rows[0] ? mapFloorRow(result.rows[0]) : null;
  }

  async delete(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM floors WHERE id = $1', [id]);
    return asNumber(result.rowCount ?? 0) > 0;
  }
}
