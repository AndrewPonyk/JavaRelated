import { Router } from 'express';

import { PositioningEventController } from '../controllers/positioning.controller';
import { validate } from '../middleware/validate.middleware';
import { uuidParamsSchema } from '../schemas/common.schema';
import {
  createPositioningEventSchema,
  ingestPositioningEventSchema,
  listPositioningEventsQuerySchema,
  updatePositioningEventSchema,
} from '../schemas/positioning.schema';

const controller = new PositioningEventController();

export const positioningEventRouter = Router();

positioningEventRouter.get(
  '/',
  validate('query', listPositioningEventsQuerySchema),
  controller.list,
);
positioningEventRouter.get('/:id', validate('params', uuidParamsSchema), controller.get);
positioningEventRouter.post('/', validate('body', createPositioningEventSchema), controller.create);
positioningEventRouter.post(
  '/ingest',
  validate('body', ingestPositioningEventSchema),
  controller.ingest,
);
positioningEventRouter.patch(
  '/:id',
  validate('params', uuidParamsSchema),
  validate('body', updatePositioningEventSchema),
  controller.update,
);
positioningEventRouter.delete('/:id', validate('params', uuidParamsSchema), controller.delete);
