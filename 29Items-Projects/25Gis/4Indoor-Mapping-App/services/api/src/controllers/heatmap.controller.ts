import type { NextFunction, Request, Response } from 'express';

import { pool } from '../db/pool';
import { ApiError } from '../middleware/error.middleware';
import type {
  CreateHeatmapCellInput,
  ListHeatmapCellsQuery,
  UpdateHeatmapCellInput,
} from '../schemas/heatmap.schema';
import { HeatmapCellService } from '../services/heatmap.service';

const heatmapCellService = new HeatmapCellService(pool);

export class HeatmapCellController {
  list = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await heatmapCellService.list(req.query as unknown as ListHeatmapCellsQuery);
      res.json({ data });
    } catch (error) {
      next(error);
    }
  };

  get = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const cell = await heatmapCellService.getById(req.params.id);
      if (!cell) {
        throw new ApiError(404, 'heatmap_cell_not_found', 'Heatmap cell was not found.');
      }

      res.json({ data: cell });
    } catch (error) {
      next(error);
    }
  };

  create = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const cell = await heatmapCellService.create(req.body as CreateHeatmapCellInput);
      res.status(201).json({ data: cell });
    } catch (error) {
      next(error);
    }
  };

  update = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const cell = await heatmapCellService.update(
        req.params.id,
        req.body as UpdateHeatmapCellInput,
      );
      if (!cell) {
        throw new ApiError(404, 'heatmap_cell_not_found', 'Heatmap cell was not found.');
      }

      res.json({ data: cell });
    } catch (error) {
      next(error);
    }
  };

  delete = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const deleted = await heatmapCellService.delete(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'heatmap_cell_not_found', 'Heatmap cell was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };
}
