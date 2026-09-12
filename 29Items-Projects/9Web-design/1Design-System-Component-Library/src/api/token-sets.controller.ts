import { Router } from 'express';
import type { TokenService } from '../services/token.service';
import { createTokenSetSchema, updateTokenSetSchema } from './validation';
import { paginate, parsePagination } from './pagination';

export function createTokenSetRouter(tokenService: TokenService): Router {
  const router = Router();

  router.get('/', async (request, response, next) => {
    try {
      const pagination = parsePagination(request, response);
      if (!pagination) {
        return;
      }

      response.json(paginate(await tokenService.listTokenSets(), pagination));
    } catch (error) {
      next(error);
    }
  });

  router.post('/', async (request, response, next) => {
    try {
      const parsed = createTokenSetSchema.safeParse(request.body);
      if (!parsed.success) {
        response.status(400).json({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Token set payload is invalid',
            details: parsed.error.flatten()
          }
        });
        return;
      }

      response.status(201).json({
        data: await tokenService.createTokenSet(parsed.data, request.header('x-actor') ?? 'api')
      });
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id', async (request, response, next) => {
    try {
      response.json({ data: await tokenService.getTokenSet(request.params.id) });
    } catch (error) {
      next(error);
    }
  });

  router.patch('/:id', async (request, response, next) => {
    try {
      const parsed = updateTokenSetSchema.safeParse(request.body);
      if (!parsed.success) {
        response.status(400).json({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Token set update payload is invalid',
            details: parsed.error.flatten()
          }
        });
        return;
      }

      response.json({
        data: await tokenService.updateTokenSet(
          request.params.id,
          parsed.data,
          request.header('x-actor') ?? 'api'
        )
      });
    } catch (error) {
      next(error);
    }
  });

  router.delete('/:id', async (request, response, next) => {
    try {
      await tokenService.deleteTokenSet(request.params.id, request.header('x-actor') ?? 'api');
      response.status(204).send();
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id/diff/:compareId', async (request, response, next) => {
    try {
      response.json({
        data: await tokenService.diffTokenSets(request.params.id, request.params.compareId)
      });
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id/audit', async (request, response, next) => {
    try {
      response.json({
        data: await tokenService.listAuditEvents({ tokenSetId: request.params.id })
      });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
