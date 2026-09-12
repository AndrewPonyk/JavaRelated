import { Router } from 'express';

import { FloorController } from '../controllers/floor.controller';
import { validate } from '../middleware/validate.middleware';
import { uuidParamsSchema } from '../schemas/common.schema';
import {
  createFloorSchema,
  listFloorsQuerySchema,
  updateFloorSchema,
} from '../schemas/floor.schema';

const controller = new FloorController();

export const floorRouter = Router();

floorRouter.get('/', validate('query', listFloorsQuerySchema), controller.list);
floorRouter.get('/:id', validate('params', uuidParamsSchema), controller.get);
floorRouter.post('/', validate('body', createFloorSchema), controller.create);
floorRouter.patch(
  '/:id',
  validate('params', uuidParamsSchema),
  validate('body', updateFloorSchema),
  controller.update,
);
floorRouter.delete('/:id', validate('params', uuidParamsSchema), controller.delete);
