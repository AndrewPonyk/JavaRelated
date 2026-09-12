import { Router } from 'express';

import { HeatmapCellController } from '../controllers/heatmap.controller';
import { validate } from '../middleware/validate.middleware';
import { uuidParamsSchema } from '../schemas/common.schema';
import {
  createHeatmapCellSchema,
  listHeatmapCellsQuerySchema,
  updateHeatmapCellSchema,
} from '../schemas/heatmap.schema';

const controller = new HeatmapCellController();

export const heatmapCellRouter = Router();

heatmapCellRouter.get('/', validate('query', listHeatmapCellsQuerySchema), controller.list);
heatmapCellRouter.get('/:id', validate('params', uuidParamsSchema), controller.get);
heatmapCellRouter.post('/', validate('body', createHeatmapCellSchema), controller.create);
heatmapCellRouter.patch(
  '/:id',
  validate('params', uuidParamsSchema),
  validate('body', updateHeatmapCellSchema),
  controller.update,
);
heatmapCellRouter.delete('/:id', validate('params', uuidParamsSchema), controller.delete);
