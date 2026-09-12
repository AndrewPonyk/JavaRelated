import type { Request, Response } from 'express';

export interface Pagination {
  limit: number;
  offset: number;
}

export interface PaginatedResponse<T> {
  data: T[];
  meta: {
    limit: number;
    offset: number;
    total: number;
  };
}

const defaultLimit = 25;
const maxLimit = 100;

export function parsePagination(request: Request, response: Response): Pagination | undefined {
  const rawLimit = request.query.limit;
  const rawOffset = request.query.offset;

  const limit = rawLimit === undefined ? defaultLimit : Number(rawLimit);
  const offset = rawOffset === undefined ? 0 : Number(rawOffset);

  if (!Number.isInteger(limit) || limit < 1 || limit > maxLimit) {
    response.status(400).json({
      error: {
        code: 'INVALID_PAGINATION',
        message: `limit must be an integer between 1 and ${maxLimit}`
      }
    });
    return undefined;
  }

  if (!Number.isInteger(offset) || offset < 0) {
    response.status(400).json({
      error: {
        code: 'INVALID_PAGINATION',
        message: 'offset must be a non-negative integer'
      }
    });
    return undefined;
  }

  return { limit, offset };
}

export function paginate<T>(items: T[], pagination: Pagination): PaginatedResponse<T> {
  return {
    data: items.slice(pagination.offset, pagination.offset + pagination.limit),
    meta: {
      limit: pagination.limit,
      offset: pagination.offset,
      total: items.length
    }
  };
}
