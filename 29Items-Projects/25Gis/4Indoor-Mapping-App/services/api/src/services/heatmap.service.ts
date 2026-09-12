import type { Database } from '../db/pool';
import { asNumber, asString, type DbRow } from '../db/rows';
import type {
  CreateHeatmapCellInput,
  ListHeatmapCellsQuery,
  UpdateHeatmapCellInput,
} from '../schemas/heatmap.schema';

export interface HeatmapCellDto {
  id: string;
  venueId: string;
  floorId: string;
  cell: unknown;
  densityScore: number;
  calculatedAt: string;
  createdAt: string;
}

function mapHeatmapCellRow(row: DbRow): HeatmapCellDto {
  return {
    id: asString(row.id),
    venueId: asString(row.venue_id),
    floorId: asString(row.floor_id),
    cell: row.cell,
    densityScore: asNumber(row.density_score),
    calculatedAt: new Date(asString(row.calculated_at)).toISOString(),
    createdAt: new Date(asString(row.created_at)).toISOString(),
  };
}

const heatmapCellSelect = `
  SELECT
    id,
    venue_id,
    floor_id,
    ST_AsGeoJSON(cell::geometry)::json AS cell,
    density_score,
    calculated_at,
    created_at
  FROM heatmap_cells
`;

export class HeatmapCellService {
  constructor(private readonly db: Database) {}

  async list(query: ListHeatmapCellsQuery): Promise<HeatmapCellDto[]> {
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

    if (query.latestOnly) {
      const result = await this.db.query(
        `
          SELECT DISTINCT ON (venue_id, floor_id, ST_AsEWKB(cell::geometry))
            id,
            venue_id,
            floor_id,
            ST_AsGeoJSON(cell::geometry)::json AS cell,
            density_score,
            calculated_at,
            created_at
          FROM heatmap_cells
          ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
          ORDER BY venue_id, floor_id, ST_AsEWKB(cell::geometry), calculated_at DESC
          LIMIT $${values.length}
        `,
        values,
      );

      return result.rows.map(mapHeatmapCellRow);
    }

    const result = await this.db.query(
      `
        ${heatmapCellSelect}
        ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
        ORDER BY calculated_at DESC
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapHeatmapCellRow);
  }

  async getById(id: string): Promise<HeatmapCellDto | null> {
    const result = await this.db.query(`${heatmapCellSelect} WHERE id = $1`, [id]);
    return result.rows[0] ? mapHeatmapCellRow(result.rows[0]) : null;
  }

  async create(input: CreateHeatmapCellInput): Promise<HeatmapCellDto> {
    const calculatedAt = input.calculatedAt ?? new Date().toISOString();
    const result = await this.db.query(
      `
        INSERT INTO heatmap_cells (venue_id, floor_id, cell, density_score, calculated_at)
        VALUES ($1, $2, ST_GeogFromGeoJSON($3), $4, $5)
        RETURNING
          id,
          venue_id,
          floor_id,
          ST_AsGeoJSON(cell::geometry)::json AS cell,
          density_score,
          calculated_at,
          created_at
      `,
      [input.venueId, input.floorId, JSON.stringify(input.cell), input.densityScore, calculatedAt],
    );

    return mapHeatmapCellRow(result.rows[0]);
  }

  async update(id: string, input: UpdateHeatmapCellInput): Promise<HeatmapCellDto | null> {
    const existing = await this.getById(id);
    if (!existing) {
      return null;
    }

    const result = await this.db.query(
      `
        UPDATE heatmap_cells
        SET
          floor_id = $2,
          cell = CASE WHEN $3::jsonb IS NULL THEN cell ELSE ST_GeogFromGeoJSON($3::text) END,
          density_score = $4,
          calculated_at = $5
        WHERE id = $1
        RETURNING
          id,
          venue_id,
          floor_id,
          ST_AsGeoJSON(cell::geometry)::json AS cell,
          density_score,
          calculated_at,
          created_at
      `,
      [
        id,
        input.floorId ?? existing.floorId,
        input.cell ? JSON.stringify(input.cell) : null,
        input.densityScore ?? existing.densityScore,
        input.calculatedAt ?? existing.calculatedAt,
      ],
    );

    return result.rows[0] ? mapHeatmapCellRow(result.rows[0]) : null;
  }

  async delete(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM heatmap_cells WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }
}
