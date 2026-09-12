import type { NextFunction, Request, Response } from 'express';

import { pool } from '../db/pool';
import { ApiError } from '../middleware/error.middleware';
import type {
  CreatePositioningEventInput,
  IngestPositioningEventInput,
  ListPositioningEventsQuery,
  UpdatePositioningEventInput,
} from '../schemas/positioning.schema';
import { PositioningEventService } from '../services/positioning.service';

const positioningEventService = new PositioningEventService(pool);

export class PositioningEventController {
  list = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await positioningEventService.list(
        req.query as unknown as ListPositioningEventsQuery,
      );
      res.json({ data });
    } catch (error) {
      next(error);
    }
  };

  get = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const event = await positioningEventService.getById(req.params.id);
      if (!event) {
        throw new ApiError(404, 'positioning_event_not_found', 'Positioning event was not found.');
      }

      res.json({ data: event });
    } catch (error) {
      next(error);
    }
  };

  create = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const event = await positioningEventService.create(req.body as CreatePositioningEventInput);
      res.status(201).json({ data: event });
    } catch (error) {
      next(error);
    }
  };

  ingest = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const event = await positioningEventService.ingest(req.body as IngestPositioningEventInput);
      res.status(201).json({ data: event });
    } catch (error) {
      next(error);
    }
  };

  update = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const event = await positioningEventService.update(
        req.params.id,
        req.body as UpdatePositioningEventInput,
      );
      if (!event) {
        throw new ApiError(404, 'positioning_event_not_found', 'Positioning event was not found.');
      }

      res.json({ data: event });
    } catch (error) {
      next(error);
    }
  };

  delete = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const deleted = await positioningEventService.delete(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'positioning_event_not_found', 'Positioning event was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };
}
