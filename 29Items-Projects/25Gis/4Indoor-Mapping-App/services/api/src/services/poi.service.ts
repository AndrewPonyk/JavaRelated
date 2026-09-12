import type { Database } from '../db/pool';
import { asNumber, asNullableString, asString, type DbRow } from '../db/rows';
import type { CreatePoiInput, ListPoiQuery, UpdatePoiInput } from '../schemas/poi.schema';

export interface PoiDto {
  id: string;
  venueId: string;
  floorId: string;
  name: string;
  category: string;
  description?: string | null;
  latitude: number;
  longitude: number;
  distanceMeters?: number | null;
  createdAt?: string;
  updatedAt?: string;
}

function mapPoiRow(row: DbRow): PoiDto {
  return {
    id: asString(row.id),
    venueId: asString(row.venue_id),
    floorId: asString(row.floor_id),
    name: asString(row.name),
    category: asString(row.category),
    description: asNullableString(row.description),
    latitude: asNumber(row.latitude),
    longitude: asNumber(row.longitude),
    distanceMeters: row.distance_meters == null ? null : asNumber(row.distance_meters),
    createdAt:
      row.created_at == null ? undefined : new Date(asString(row.created_at)).toISOString(),
    updatedAt:
      row.updated_at == null ? undefined : new Date(asString(row.updated_at)).toISOString(),
  };
}

const poiSelect = `
  SELECT
    id,
    venue_id,
    floor_id,
    name,
    category,
    description,
    ST_Y(location::geometry) AS latitude,
    ST_X(location::geometry) AS longitude,
    created_at,
    updated_at
`;

export class PoiService {
  constructor(private readonly db: Database) {}

  async list(query: ListPoiQuery): Promise<PoiDto[]> {
    const values: unknown[] = [query.venueId];
    const where = ['venue_id = $1'];
    let distanceSelect = 'NULL::double precision AS distance_meters';
    let orderBy = 'name ASC';

    if (query.floorId) {
      values.push(query.floorId);
      where.push(`floor_id = $${values.length}`);
    }

    if (query.category) {
      values.push(query.category);
      where.push(`category = $${values.length}`);
    }

    if (query.query) {
      values.push(`%${query.query}%`);
      where.push(`(name ILIKE $${values.length} OR description ILIKE $${values.length})`);
    }

    if (query.latitude != null && query.longitude != null) {
      values.push(query.longitude, query.latitude);
      const longitudeParam = values.length - 1;
      const latitudeParam = values.length;
      const point = `ST_SetSRID(ST_MakePoint($${longitudeParam}, $${latitudeParam}), 4326)::geography`;
      distanceSelect = `ST_Distance(location, ${point}) AS distance_meters`;
      orderBy = 'distance_meters ASC, name ASC';
    }

    values.push(query.limit);

    const result = await this.db.query(
      `
        ${poiSelect},
          ${distanceSelect}
        FROM pois
        WHERE ${where.join(' AND ')}
        ORDER BY ${orderBy}
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapPoiRow);
  }

  async getById(id: string): Promise<PoiDto | null> {
    const result = await this.db.query(
      `
        ${poiSelect},
          NULL::double precision AS distance_meters
        FROM pois
        WHERE id = $1
      `,
      [id],
    );

    return result.rows[0] ? mapPoiRow(result.rows[0]) : null;
  }

  async create(input: CreatePoiInput): Promise<PoiDto> {
    const result = await this.db.query(
      `
        INSERT INTO pois (
          venue_id,
          floor_id,
          name,
          category,
          description,
          location
        )
        VALUES (
          $1,
          $2,
          $3,
          $4,
          $5,
          ST_SetSRID(ST_MakePoint($6, $7), 4326)::geography
        )
        RETURNING
          id,
          venue_id,
          floor_id,
          name,
          category,
          description,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          NULL::double precision AS distance_meters,
          created_at,
          updated_at
      `,
      [
        input.venueId,
        input.floorId,
        input.name,
        input.category,
        input.description ?? null,
        input.longitude,
        input.latitude,
      ],
    );

    return mapPoiRow(result.rows[0]);
  }

  async update(id: string, input: UpdatePoiInput): Promise<PoiDto | null> {
    const existing = await this.getById(id);
    if (!existing) {
      return null;
    }

    const next = {
      floorId: input.floorId ?? existing.floorId,
      name: input.name ?? existing.name,
      category: input.category ?? existing.category,
      description: input.description ?? existing.description,
      latitude: input.latitude ?? existing.latitude,
      longitude: input.longitude ?? existing.longitude,
    };

    const result = await this.db.query(
      `
        UPDATE pois
        SET
          floor_id = $2,
          name = $3,
          category = $4,
          description = $5,
          location = ST_SetSRID(ST_MakePoint($6, $7), 4326)::geography,
          updated_at = now()
        WHERE id = $1
        RETURNING
          id,
          venue_id,
          floor_id,
          name,
          category,
          description,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          NULL::double precision AS distance_meters,
          created_at,
          updated_at
      `,
      [id, next.floorId, next.name, next.category, next.description, next.longitude, next.latitude],
    );

    return mapPoiRow(result.rows[0]);
  }

  async delete(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM pois WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }
}
