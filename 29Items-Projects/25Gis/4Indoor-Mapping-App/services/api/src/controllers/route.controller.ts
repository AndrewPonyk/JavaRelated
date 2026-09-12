import type { NextFunction, Request, Response } from 'express';

import { pool } from '../db/pool';
import { ApiError } from '../middleware/error.middleware';
import type {
  CreateRouteEdgeInput,
  CreateRouteNodeInput,
  DirectionsQuery,
  ListRouteEdgesQuery,
  ListRouteNodesQuery,
  UpdateRouteEdgeInput,
  UpdateRouteNodeInput,
} from '../schemas/route.schema';
import { RouteService } from '../services/route.service';

const routeService = new RouteService(pool);

export class RouteController {
  listNodes = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await routeService.listNodes(req.query as unknown as ListRouteNodesQuery);
      res.json({ data });
    } catch (error) {
      next(error);
    }
  };

  getNode = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const node = await routeService.getNodeById(req.params.id);
      if (!node) {
        throw new ApiError(404, 'route_node_not_found', 'Route node was not found.');
      }

      res.json({ data: node });
    } catch (error) {
      next(error);
    }
  };

  createNode = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const node = await routeService.createNode(req.body as CreateRouteNodeInput);
      res.status(201).json({ data: node });
    } catch (error) {
      next(error);
    }
  };

  updateNode = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const node = await routeService.updateNode(req.params.id, req.body as UpdateRouteNodeInput);
      if (!node) {
        throw new ApiError(404, 'route_node_not_found', 'Route node was not found.');
      }

      res.json({ data: node });
    } catch (error) {
      next(error);
    }
  };

  deleteNode = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const deleted = await routeService.deleteNode(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'route_node_not_found', 'Route node was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };

  listEdges = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await routeService.listEdges(req.query as unknown as ListRouteEdgesQuery);
      res.json({ data });
    } catch (error) {
      next(error);
    }
  };

  getEdge = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const edge = await routeService.getEdgeById(req.params.id);
      if (!edge) {
        throw new ApiError(404, 'route_edge_not_found', 'Route edge was not found.');
      }

      res.json({ data: edge });
    } catch (error) {
      next(error);
    }
  };

  createEdge = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const edge = await routeService.createEdge(req.body as CreateRouteEdgeInput);
      if (!edge) {
        throw new ApiError(400, 'route_edge_invalid_nodes', 'Both nodes must belong to the venue.');
      }

      res.status(201).json({ data: edge });
    } catch (error) {
      next(error);
    }
  };

  updateEdge = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const edge = await routeService.updateEdge(req.params.id, req.body as UpdateRouteEdgeInput);
      if (!edge) {
        throw new ApiError(404, 'route_edge_not_found', 'Route edge was not found.');
      }

      res.json({ data: edge });
    } catch (error) {
      next(error);
    }
  };

  deleteEdge = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const deleted = await routeService.deleteEdge(req.params.id);
      if (!deleted) {
        throw new ApiError(404, 'route_edge_not_found', 'Route edge was not found.');
      }

      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };

  directions = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = await routeService.getDirections(req.query as unknown as DirectionsQuery);
      if (!data) {
        throw new ApiError(404, 'route_not_found', 'No route connects the requested nodes.');
      }

      res.json({ data });
    } catch (error) {
      next(error);
    }
  };
}
