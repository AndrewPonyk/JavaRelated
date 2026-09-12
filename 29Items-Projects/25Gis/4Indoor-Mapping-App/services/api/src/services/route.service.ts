import type { Database } from '../db/pool';
import { asBoolean, asNumber, asString, type DbRow } from '../db/rows';
import type {
  CreateRouteEdgeInput,
  CreateRouteNodeInput,
  DirectionsQuery,
  ListRouteEdgesQuery,
  ListRouteNodesQuery,
  UpdateRouteEdgeInput,
  UpdateRouteNodeInput,
} from '../schemas/route.schema';

export interface RouteNodeDto {
  id: string;
  venueId: string;
  floorId: string;
  nodeType: string;
  latitude: number;
  longitude: number;
  createdAt: string;
}

export interface RouteEdgeDto {
  id: string;
  venueId: string;
  fromNodeId: string;
  toNodeId: string;
  travelCost: number;
  isAccessible: boolean;
  geometry: unknown;
  createdAt: string;
}

export interface DirectionsDto {
  venueId: string;
  fromNodeId: string;
  toNodeId: string;
  totalCost: number;
  nodeIds: string[];
  edges: RouteEdgeDto[];
}

function mapRouteNodeRow(row: DbRow): RouteNodeDto {
  return {
    id: asString(row.id),
    venueId: asString(row.venue_id),
    floorId: asString(row.floor_id),
    nodeType: asString(row.node_type),
    latitude: asNumber(row.latitude),
    longitude: asNumber(row.longitude),
    createdAt: new Date(asString(row.created_at)).toISOString(),
  };
}

function mapRouteEdgeRow(row: DbRow): RouteEdgeDto {
  return {
    id: asString(row.id),
    venueId: asString(row.venue_id),
    fromNodeId: asString(row.from_node_id),
    toNodeId: asString(row.to_node_id),
    travelCost: asNumber(row.travel_cost),
    isAccessible: asBoolean(row.is_accessible),
    geometry: row.geometry,
    createdAt: new Date(asString(row.created_at)).toISOString(),
  };
}

const routeNodeSelect = `
  SELECT
    id,
    venue_id,
    floor_id,
    node_type,
    ST_Y(location::geometry) AS latitude,
    ST_X(location::geometry) AS longitude,
    created_at
  FROM route_nodes
`;

const routeEdgeSelect = `
  SELECT
    id,
    venue_id,
    from_node_id,
    to_node_id,
    travel_cost,
    is_accessible,
    ST_AsGeoJSON(geometry::geometry)::json AS geometry,
    created_at
  FROM route_edges
`;

export class RouteService {
  constructor(private readonly db: Database) {}

  async listNodes(query: ListRouteNodesQuery): Promise<RouteNodeDto[]> {
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

    if (query.nodeType) {
      values.push(query.nodeType);
      where.push(`node_type = $${values.length}`);
    }

    values.push(query.limit);

    const result = await this.db.query(
      `
        ${routeNodeSelect}
        ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
        ORDER BY created_at ASC
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapRouteNodeRow);
  }

  async getNodeById(id: string): Promise<RouteNodeDto | null> {
    const result = await this.db.query(`${routeNodeSelect} WHERE id = $1`, [id]);
    return result.rows[0] ? mapRouteNodeRow(result.rows[0]) : null;
  }

  async createNode(input: CreateRouteNodeInput): Promise<RouteNodeDto> {
    const result = await this.db.query(
      `
        INSERT INTO route_nodes (venue_id, floor_id, node_type, location)
        VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326)::geography)
        RETURNING
          id,
          venue_id,
          floor_id,
          node_type,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          created_at
      `,
      [input.venueId, input.floorId, input.nodeType, input.longitude, input.latitude],
    );

    return mapRouteNodeRow(result.rows[0]);
  }

  async updateNode(id: string, input: UpdateRouteNodeInput): Promise<RouteNodeDto | null> {
    const existing = await this.getNodeById(id);
    if (!existing) {
      return null;
    }

    const result = await this.db.query(
      `
        UPDATE route_nodes
        SET
          floor_id = $2,
          node_type = $3,
          location = ST_SetSRID(ST_MakePoint($4, $5), 4326)::geography
        WHERE id = $1
        RETURNING
          id,
          venue_id,
          floor_id,
          node_type,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          created_at
      `,
      [
        id,
        input.floorId ?? existing.floorId,
        input.nodeType ?? existing.nodeType,
        input.longitude ?? existing.longitude,
        input.latitude ?? existing.latitude,
      ],
    );

    return result.rows[0] ? mapRouteNodeRow(result.rows[0]) : null;
  }

  async deleteNode(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM route_nodes WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }

  async listEdges(query: ListRouteEdgesQuery): Promise<RouteEdgeDto[]> {
    const values: unknown[] = [];
    const where: string[] = [];

    if (query.venueId) {
      values.push(query.venueId);
      where.push(`venue_id = $${values.length}`);
    }

    if (query.fromNodeId) {
      values.push(query.fromNodeId);
      where.push(`from_node_id = $${values.length}`);
    }

    if (query.toNodeId) {
      values.push(query.toNodeId);
      where.push(`to_node_id = $${values.length}`);
    }

    if (query.accessibleOnly) {
      where.push('is_accessible = true');
    }

    values.push(query.limit);

    const result = await this.db.query(
      `
        ${routeEdgeSelect}
        ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
        ORDER BY created_at ASC
        LIMIT $${values.length}
      `,
      values,
    );

    return result.rows.map(mapRouteEdgeRow);
  }

  async getEdgeById(id: string): Promise<RouteEdgeDto | null> {
    const result = await this.db.query(`${routeEdgeSelect} WHERE id = $1`, [id]);
    return result.rows[0] ? mapRouteEdgeRow(result.rows[0]) : null;
  }

  async createEdge(input: CreateRouteEdgeInput): Promise<RouteEdgeDto | null> {
    const result = await this.db.query(
      `
        INSERT INTO route_edges (
          venue_id,
          from_node_id,
          to_node_id,
          travel_cost,
          is_accessible,
          geometry
        )
        SELECT
          $1,
          $2,
          $3,
          $4,
          $5,
          ST_GeogFromGeoJSON($6)
        WHERE EXISTS (
          SELECT 1 FROM route_nodes from_node
          JOIN route_nodes to_node ON to_node.id = $3
          WHERE from_node.id = $2
            AND from_node.venue_id = $1
            AND to_node.venue_id = $1
        )
        RETURNING
          id,
          venue_id,
          from_node_id,
          to_node_id,
          travel_cost,
          is_accessible,
          ST_AsGeoJSON(geometry::geometry)::json AS geometry,
          created_at
      `,
      [
        input.venueId,
        input.fromNodeId,
        input.toNodeId,
        input.travelCost,
        input.isAccessible,
        JSON.stringify(input.geometry),
      ],
    );

    return result.rows[0] ? mapRouteEdgeRow(result.rows[0]) : null;
  }

  async updateEdge(id: string, input: UpdateRouteEdgeInput): Promise<RouteEdgeDto | null> {
    const existing = await this.getEdgeById(id);
    if (!existing) {
      return null;
    }

    const result = await this.db.query(
      `
        UPDATE route_edges
        SET
          from_node_id = $2,
          to_node_id = $3,
          travel_cost = $4,
          is_accessible = $5,
          geometry = CASE WHEN $6::jsonb IS NULL THEN geometry ELSE ST_GeogFromGeoJSON($6::text) END
        WHERE id = $1
        RETURNING
          id,
          venue_id,
          from_node_id,
          to_node_id,
          travel_cost,
          is_accessible,
          ST_AsGeoJSON(geometry::geometry)::json AS geometry,
          created_at
      `,
      [
        id,
        input.fromNodeId ?? existing.fromNodeId,
        input.toNodeId ?? existing.toNodeId,
        input.travelCost ?? existing.travelCost,
        input.isAccessible ?? existing.isAccessible,
        input.geometry ? JSON.stringify(input.geometry) : null,
      ],
    );

    return result.rows[0] ? mapRouteEdgeRow(result.rows[0]) : null;
  }

  async deleteEdge(id: string): Promise<boolean> {
    const result = await this.db.query('DELETE FROM route_edges WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }

  async getDirections(query: DirectionsQuery): Promise<DirectionsDto | null> {
    const result = await this.db.query(
      `
        ${routeEdgeSelect}
        WHERE venue_id = $1
          AND ($2::boolean = false OR is_accessible = true)
      `,
      [query.venueId, query.accessibleOnly],
    );

    const edges = result.rows.map(mapRouteEdgeRow);
    const byFrom = new Map<string, RouteEdgeDto[]>();
    for (const edge of edges) {
      const bucket = byFrom.get(edge.fromNodeId) ?? [];
      bucket.push(edge);
      byFrom.set(edge.fromNodeId, bucket);
    }

    const distances = new Map<string, number>([[query.fromNodeId, 0]]);
    const previous = new Map<string, RouteEdgeDto>();
    const visited = new Set<string>();
    const queue = new Set<string>([query.fromNodeId]);

    while (queue.size > 0) {
      let current: string | null = null;
      let bestDistance = Number.POSITIVE_INFINITY;
      for (const candidate of queue) {
        const distance = distances.get(candidate) ?? Number.POSITIVE_INFINITY;
        if (distance < bestDistance) {
          current = candidate;
          bestDistance = distance;
        }
      }

      if (current == null || current === query.toNodeId) {
        break;
      }

      queue.delete(current);
      visited.add(current);

      for (const edge of byFrom.get(current) ?? []) {
        if (visited.has(edge.toNodeId)) {
          continue;
        }

        const nextDistance = bestDistance + edge.travelCost;
        if (nextDistance < (distances.get(edge.toNodeId) ?? Number.POSITIVE_INFINITY)) {
          distances.set(edge.toNodeId, nextDistance);
          previous.set(edge.toNodeId, edge);
          queue.add(edge.toNodeId);
        }
      }
    }

    const totalCost = distances.get(query.toNodeId);
    if (totalCost == null) {
      return null;
    }

    const pathEdges: RouteEdgeDto[] = [];
    let cursor = query.toNodeId;
    while (cursor !== query.fromNodeId) {
      const edge = previous.get(cursor);
      if (!edge) {
        return null;
      }
      pathEdges.unshift(edge);
      cursor = edge.fromNodeId;
    }

    return {
      venueId: query.venueId,
      fromNodeId: query.fromNodeId,
      toNodeId: query.toNodeId,
      totalCost,
      nodeIds: [query.fromNodeId, ...pathEdges.map((edge) => edge.toNodeId)],
      edges: pathEdges,
    };
  }
}
