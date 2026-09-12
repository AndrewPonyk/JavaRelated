import type { NextFunction, Request, Response } from 'express';

import { pool } from '../db/pool';
import { ApiError } from '../middleware/error.middleware';
import type {
  CreateBeaconAnchorInput,
  ListBeaconAnchorsQuery,
  UpdateBeaconAnchorInput,
} from '../schemas/beacon.schema';
import { BeaconAnchorService } from '../services/beacon.service';

const beaconAnchorService = new BeaconAnchorService(pool);

export class BeaconAnchorController {
  list = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await beaconAnchorService.list(req.query as unknown as ListBeaconAnchorsQuery);
      res.json({ data });
    } catch (error) {
      next(error);
    }
  };

  get = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const anchor = await beaconAnchorService.getById(req.params.id);
      if (!anchor) {
        throw new ApiError(404, 'beacon_anchor_not_found', 'Beacon anchor was not found.');
      }

      res.json({ data: anchor });
    } catch (error) {
      next(error);
    }
  };

  create = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const anchor = await beaconAnchorService.create(req.body as CreateBeaconAnchorInput);
      res.status(201).json({ data: anchor });
    } catch (error) {
      next(error);
    }
  };

  update = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const anchor = await beaconAnchorService.update(
        req.params.id,
        req.body as UpdateBeaconAnchorInput,
      );
      if (!anchor) {
        throw new ApiError(404, 'beacon_anchor_not_found', 'Beacon anchor was not found.');
      }

      res.json({ data: anchor });
    } catch (error) {
      next(error);
    }
  };

  delete = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const deleted = await beaconAnchorService.delete(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'beacon_anchor_not_found', 'Beacon anchor was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };
}
