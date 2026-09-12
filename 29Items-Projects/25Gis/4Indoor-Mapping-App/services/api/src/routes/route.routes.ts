import { Router } from 'express';

import { RouteController } from '../controllers/route.controller';
import { validate } from '../middleware/validate.middleware';
import { uuidParamsSchema } from '../schemas/common.schema';
import {
  createRouteEdgeSchema,
  createRouteNodeSchema,
  directionsQuerySchema,
  listRouteEdgesQuerySchema,
  listRouteNodesQuerySchema,
  updateRouteEdgeSchema,
  updateRouteNodeSchema,
} from '../schemas/route.schema';

const controller = new RouteController();

export const routeRouter = Router();

routeRouter.get('/directions', validate('query', directionsQuerySchema), controller.directions);

routeRouter.get('/nodes', validate('query', listRouteNodesQuerySchema), controller.listNodes);
routeRouter.get('/nodes/:id', validate('params', uuidParamsSchema), controller.getNode);
routeRouter.post('/nodes', validate('body', createRouteNodeSchema), controller.createNode);
routeRouter.patch(
  '/nodes/:id',
  validate('params', uuidParamsSchema),
  validate('body', updateRouteNodeSchema),
  controller.updateNode,
);
routeRouter.delete('/nodes/:id', validate('params', uuidParamsSchema), controller.deleteNode);

routeRouter.get('/edges', validate('query', listRouteEdgesQuerySchema), controller.listEdges);
routeRouter.get('/edges/:id', validate('params', uuidParamsSchema), controller.getEdge);
routeRouter.post('/edges', validate('body', createRouteEdgeSchema), controller.createEdge);
routeRouter.patch(
  '/edges/:id',
  validate('params', uuidParamsSchema),
  validate('body', updateRouteEdgeSchema),
  controller.updateEdge,
);
routeRouter.delete('/edges/:id', validate('params', uuidParamsSchema), controller.deleteEdge);
