import type { NextFunction, Request, Response } from 'express';

import { pool } from '../db/pool';
import { ApiError } from '../middleware/error.middleware';
import type { CreatePoiInput, ListPoiQuery, UpdatePoiInput } from '../schemas/poi.schema';
import { PoiService } from '../services/poi.service';

const poiService = new PoiService(pool);

export class PoiController {
  async list(req: Request, res: Response, next: NextFunction) {
    try {
      const data = await poiService.list(req.query as unknown as ListPoiQuery);
      res.json({ data });
    } catch (error) {
      next(error);
    }
  }

  async get(req: Request, res: Response, next: NextFunction) {
    try {
      const poi = await poiService.getById(req.params.id);
      if (!poi) {
        throw new ApiError(404, 'poi_not_found', 'POI was not found.');
      }

      res.json({ data: poi });
    } catch (error) {
      next(error);
    }
  }

  async create(req: Request, res: Response, next: NextFunction) {
    try {
      const poi = await poiService.create(req.body as CreatePoiInput);
      res.status(201).json({ data: poi });
    } catch (error) {
      next(error);
    }
  }

  async update(req: Request, res: Response, next: NextFunction) {
    try {
      const poi = await poiService.update(req.params.id, req.body as UpdatePoiInput);
      if (!poi) {
        throw new ApiError(404, 'poi_not_found', 'POI was not found.');
      }

      res.json({ data: poi });
    } catch (error) {
      next(error);
    }
  }

  async delete(req: Request, res: Response, next: NextFunction) {
    try {
      const deleted = await poiService.delete(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'poi_not_found', 'POI was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  }
}
