import type { NextFunction, Request, Response } from 'express';

import { pool } from '../db/pool';
import { ApiError } from '../middleware/error.middleware';
import type {
  CreateWifiFingerprintInput,
  ListWifiFingerprintsQuery,
  UpdateWifiFingerprintInput,
} from '../schemas/wifi.schema';
import { WifiFingerprintService } from '../services/wifi.service';

const wifiFingerprintService = new WifiFingerprintService(pool);

export class WifiFingerprintController {
  list = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await wifiFingerprintService.list(
        req.query as unknown as ListWifiFingerprintsQuery,
      );
      res.json({ data });
    } catch (error) {
      next(error);
    }
  };

  get = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const fingerprint = await wifiFingerprintService.getById(req.params.id);
      if (!fingerprint) {
        throw new ApiError(404, 'wifi_fingerprint_not_found', 'WiFi fingerprint was not found.');
      }

      res.json({ data: fingerprint });
    } catch (error) {
      next(error);
    }
  };

  create = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const fingerprint = await wifiFingerprintService.create(
        req.body as CreateWifiFingerprintInput,
      );
      res.status(201).json({ data: fingerprint });
    } catch (error) {
      next(error);
    }
  };

  update = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const fingerprint = await wifiFingerprintService.update(
        req.params.id,
        req.body as UpdateWifiFingerprintInput,
      );
      if (!fingerprint) {
        throw new ApiError(404, 'wifi_fingerprint_not_found', 'WiFi fingerprint was not found.');
      }

      res.json({ data: fingerprint });
    } catch (error) {
      next(error);
    }
  };

  delete = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const deleted = await wifiFingerprintService.delete(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'wifi_fingerprint_not_found', 'WiFi fingerprint was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };
}
