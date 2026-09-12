import type { Database } from '../db/pool';
import { asNumber, asOptionalJson, asString, type DbRow } from '../db/rows';
import type { CreateVenueInput, ListVenuesQuery, UpdateVenueInput } from '../schemas/venue.schema';

export interface VenueDto {
  id: string;
  name: string;
  venueType: 'mall' | 'airport';
  timezone: string;
  boundary: unknown | null;
  createdAt: string;
  updatedAt: string;
}

function mapVenueRow(row: DbRow): VenueDto {
  return {
    id: asString(row.id),
    name: asString(row.name),
    venueType: asString(row.venue_type) as VenueDto['venueType'],
    timezone: asString(row.timezone),
    boundary: asOptionalJson(row.boundary),
    createdAt: new Date(asString(row.created_at)).toISOString(),
    updatedAt: new Date(asString(row.updated_at)).toISOString(),
  };
}

const venueSelect = `
  SELECT
    id,
    name,
    venue_type,
    timezone,
    CASE WHEN boundary IS NULL THEN NULL ELSE ST_AsGeoJSON(boundary::geometry)::json END AS boundary,
    created_at,
    updated_at
  FROM venues
`;

export class VenueService {
  constructor(private readonly db: Database) {}

  async list(query: ListVenuesQuery): Promise<VenueDto[]> {
    const values: unknown[] = [];
    const where: string[] = [];

    if (query.venueType) {
      values.push(query.venueType);
      where.push(`venue_type = $${values.length}`);
    }

    if (query.query) {
      values.push(`%${query.query}%`);
      where.push(`name ILIKE $${values.length}`);
    }

    values.push(query.limit);

    const result = await this.db.query(
      `
        ${venueSelect}
        ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
        ORDER BY name ASC
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapVenueRow);
  }

  async getById(id: string): Promise<VenueDto | null> {
    const result = await this.db.query(`${venueSelect} WHERE id = $1`, [id]);
    return result.rows[0] ? mapVenueRow(result.rows[0]) : null;
  }

  async create(input: CreateVenueInput): Promise<VenueDto> {
    const result = await this.db.query(
      `
        INSERT INTO venues (name, venue_type, timezone, boundary)
        VALUES (
          $1,
          $2,
          $3,
          CASE WHEN $4::jsonb IS NULL THEN NULL ELSE ST_GeogFromGeoJSON($4::text) END
        )
        RETURNING
          id,
          name,
          venue_type,
          timezone,
          CASE WHEN boundary IS NULL THEN NULL ELSE ST_AsGeoJSON(boundary::geometry)::json END AS boundary,
          created_at,
          updated_at
      `,
      [
        input.name,
        input.venueType,
        input.timezone,
        input.boundary ? JSON.stringify(input.boundary) : null,
      ],
    );

    return mapVenueRow(result.rows[0]);
  }

  async update(id: string, input: UpdateVenueInput): Promise<VenueDto | null> {
    const existing = await this.getById(id);
    if (!existing) {
      return null;
    }

    const result = await this.db.query(
      `
        UPDATE venues
        SET
          name = $2,
          venue_type = $3,
          timezone = $4,
          boundary = CASE WHEN $5::jsonb IS NULL THEN boundary ELSE ST_GeogFromGeoJSON($5::text) END,
          updated_at = now()
        WHERE id = $1
        RETURNING
          id,
          name,
          venue_type,
          timezone,
          CASE WHEN boundary IS NULL THEN NULL ELSE ST_AsGeoJSON(boundary::geometry)::json END AS boundary,
          created_at,
          updated_at
      `,
      [
        id,
        input.name ?? existing.name,
        input.venueType ?? existing.venueType,
        input.timezone ?? existing.timezone,
        input.boundary ? JSON.stringify(input.boundary) : null,
      ],
    );

    return result.rows[0] ? mapVenueRow(result.rows[0]) : null;
  }

  async delete(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM venues WHERE id = $1', [id]);
    return asNumber(result.rowCount ?? 0) > 0;
  }
}
