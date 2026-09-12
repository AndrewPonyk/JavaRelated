import { Router } from 'express';

import { VenueController } from '../controllers/venue.controller';
import { validate } from '../middleware/validate.middleware';
import { uuidParamsSchema } from '../schemas/common.schema';
import {
  createVenueSchema,
  listVenuesQuerySchema,
  updateVenueSchema,
} from '../schemas/venue.schema';

const controller = new VenueController();

export const venueRouter = Router();

venueRouter.get('/', validate('query', listVenuesQuerySchema), controller.list);
venueRouter.get('/:id', validate('params', uuidParamsSchema), controller.get);
venueRouter.post('/', validate('body', createVenueSchema), controller.create);
venueRouter.patch(
  '/:id',
  validate('params', uuidParamsSchema),
  validate('body', updateVenueSchema),
  controller.update,
);
venueRouter.delete('/:id', validate('params', uuidParamsSchema), controller.delete);
