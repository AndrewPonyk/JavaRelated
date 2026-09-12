import { Router } from 'express';

import { WifiFingerprintController } from '../controllers/wifi.controller';
import { validate } from '../middleware/validate.middleware';
import { uuidParamsSchema } from '../schemas/common.schema';
import {
  createWifiFingerprintSchema,
  listWifiFingerprintsQuerySchema,
  updateWifiFingerprintSchema,
} from '../schemas/wifi.schema';

const controller = new WifiFingerprintController();

export const wifiFingerprintRouter = Router();

wifiFingerprintRouter.get('/', validate('query', listWifiFingerprintsQuerySchema), controller.list);
wifiFingerprintRouter.get('/:id', validate('params', uuidParamsSchema), controller.get);
wifiFingerprintRouter.post('/', validate('body', createWifiFingerprintSchema), controller.create);
wifiFingerprintRouter.patch(
  '/:id',
  validate('params', uuidParamsSchema),
  validate('body', updateWifiFingerprintSchema),
  controller.update,
);
wifiFingerprintRouter.delete('/:id', validate('params', uuidParamsSchema), controller.delete);
