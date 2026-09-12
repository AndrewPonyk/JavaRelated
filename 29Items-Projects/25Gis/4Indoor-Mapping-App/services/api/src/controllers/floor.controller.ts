import type { NextFunction, Request, Response } from 'express';

import { pool } from '../db/pool';
import { ApiError } from '../middleware/error.middleware';
import type { CreateFloorInput, ListFloorsQuery, UpdateFloorInput } from '../schemas/floor.schema';
import { FloorService } from '../services/floor.service';

const floorService = new FloorService(pool);

export class FloorController {
  list = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await floorService.list(req.query as unknown as ListFloorsQuery);
      res.json({ data });
    } catch (error) {
      next(error);
    }
  };

  get = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const floor = await floorService.getById(req.params.id);
      if (!floor) {
        throw new ApiError(404, 'floor_not_found', 'Floor was not found.');
      }

      res.json({ data: floor });
    } catch (error) {
      next(error);
    }
  };

  create = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const floor = await floorService.create(req.body as CreateFloorInput);
      res.status(201).json({ data: floor });
    } catch (error) {
      next(error);
    }
  };

  update = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const floor = await floorService.update(req.params.id, req.body as UpdateFloorInput);
      if (!floor) {
        throw new ApiError(404, 'floor_not_found', 'Floor was not found.');
      }

      res.json({ data: floor });
    } catch (error) {
      next(error);
    }
  };

  delete = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const deleted = await floorService.delete(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'floor_not_found', 'Floor was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };
}
