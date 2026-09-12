import { Router } from 'express';

import { BeaconAnchorController } from '../controllers/beacon.controller';
import { validate } from '../middleware/validate.middleware';
import { uuidParamsSchema } from '../schemas/common.schema';
import {
  createBeaconAnchorSchema,
  listBeaconAnchorsQuerySchema,
  updateBeaconAnchorSchema,
} from '../schemas/beacon.schema';

const controller = new BeaconAnchorController();

export const beaconAnchorRouter = Router();

beaconAnchorRouter.get('/', validate('query', listBeaconAnchorsQuerySchema), controller.list);
beaconAnchorRouter.get('/:id', validate('params', uuidParamsSchema), controller.get);
beaconAnchorRouter.post('/', validate('body', createBeaconAnchorSchema), controller.create);
beaconAnchorRouter.patch(
  '/:id',
  validate('params', uuidParamsSchema),
  validate('body', updateBeaconAnchorSchema),
  controller.update,
);
beaconAnchorRouter.delete('/:id', validate('params', uuidParamsSchema), controller.delete);
