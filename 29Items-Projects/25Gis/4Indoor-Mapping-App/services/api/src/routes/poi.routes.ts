import { Router } from 'express';

import { PoiController } from '../controllers/poi.controller';
import { validate } from '../middleware/validate.middleware';
import {
  createPoiSchema,
  listPoiQuerySchema,
  poiIdParamsSchema,
  updatePoiSchema,
} from '../schemas/poi.schema';

const controller = new PoiController();

export const poiRouter = Router();

poiRouter.get('/', validate('query', listPoiQuerySchema), controller.list);
poiRouter.get('/:id', validate('params', poiIdParamsSchema), controller.get);
poiRouter.post('/', validate('body', createPoiSchema), controller.create);
poiRouter.patch(
  '/:id',
  validate('params', poiIdParamsSchema),
  validate('body', updatePoiSchema),
  controller.update,
);
poiRouter.delete('/:id', validate('params', poiIdParamsSchema), controller.delete);
