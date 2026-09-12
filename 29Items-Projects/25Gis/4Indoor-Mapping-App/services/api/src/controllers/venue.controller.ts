import type { NextFunction, Request, Response } from 'express';

import { pool } from '../db/pool';
import { ApiError } from '../middleware/error.middleware';
import type { CreateVenueInput, ListVenuesQuery, UpdateVenueInput } from '../schemas/venue.schema';
import { VenueService } from '../services/venue.service';

const venueService = new VenueService(pool);

export class VenueController {
  list = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await venueService.list(req.query as unknown as ListVenuesQuery);
      res.json({ data });
    } catch (error) {
      next(error);
    }
  };

  get = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const venue = await venueService.getById(req.params.id);
      if (!venue) {
        throw new ApiError(404, 'venue_not_found', 'Venue was not found.');
      }

      res.json({ data: venue });
    } catch (error) {
      next(error);
    }
  };

  create = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const venue = await venueService.create(req.body as CreateVenueInput);
      res.status(201).json({ data: venue });
    } catch (error) {
      next(error);
    }
  };

  update = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const venue = await venueService.update(req.params.id, req.body as UpdateVenueInput);
      if (!venue) {
        throw new ApiError(404, 'venue_not_found', 'Venue was not found.');
      }

      res.json({ data: venue });
    } catch (error) {
      next(error);
    }
  };

  delete = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const deleted = await venueService.delete(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'venue_not_found', 'Venue was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };
}
