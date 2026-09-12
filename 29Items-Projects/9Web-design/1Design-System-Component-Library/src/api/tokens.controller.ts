import { Router } from 'express';
import type { TokenService } from '../services/token.service';
import type { TokenCategory, TokenStatus } from '../services/token-models';
import {
  actorActionSchema,
  createTokenSchema,
  tokenCategorySchema,
  tokenStatusSchema,
  updateTokenSchema
} from './validation';
import { paginate, parsePagination } from './pagination';

function parseOptionalEnum<T extends string>(
  value: unknown,
  parser: { safeParse: (value: unknown) => { success: true; data: T } | { success: false } }
): T | undefined {
  if (value === undefined) {
    return undefined;
  }

  const parsed = parser.safeParse(value);
  if (!parsed.success) {
    throw new Error('INVALID_ENUM');
  }

  return parsed.data;
}

export function createTokenRouter(tokenService: TokenService): Router {
  const router = Router();

  router.get('/', async (request, response, next) => {
    try {
      const pagination = parsePagination(request, response);
      if (!pagination) {
        return;
      }

      let category: TokenCategory | undefined;
      let status: TokenStatus | undefined;

      try {
        category = parseOptionalEnum<TokenCategory>(request.query.category, tokenCategorySchema);
        status = parseOptionalEnum<TokenStatus>(request.query.status, tokenStatusSchema);
      } catch {
        response.status(400).json({
          error: {
            code: 'INVALID_QUERY',
            message: 'category or status query parameter is invalid'
          }
        });
        return;
      }

      const tokens = await tokenService.listTokens({
        tokenSetId:
          typeof request.query.tokenSetId === 'string' ? request.query.tokenSetId : undefined,
        category,
        status,
        search: typeof request.query.search === 'string' ? request.query.search : undefined
      });

      response.json(paginate(tokens, pagination));
    } catch (error) {
      next(error);
    }
  });

  router.get('/changelog', async (request, response, next) => {
    try {
      const pagination = parsePagination(request, response);
      if (!pagination) {
        return;
      }

      const changelog = await tokenService.listChangelog(
        typeof request.query.tokenSetId === 'string' ? request.query.tokenSetId : undefined
      );
      response.json(paginate(changelog, pagination));
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id', async (request, response, next) => {
    try {
      response.json({ data: await tokenService.getToken(request.params.id) });
    } catch (error) {
      next(error);
    }
  });

  router.post('/', async (request, response, next) => {
    try {
      const parsed = createTokenSchema.safeParse(request.body);
      if (!parsed.success) {
        response.status(400).json({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Token payload is invalid',
            details: parsed.error.flatten()
          }
        });
        return;
      }

      response.status(201).json({ data: await tokenService.createToken(parsed.data) });
    } catch (error) {
      next(error);
    }
  });

  router.patch('/:id', async (request, response, next) => {
    try {
      const parsed = updateTokenSchema.safeParse(request.body);
      if (!parsed.success) {
        response.status(400).json({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Token update payload is invalid',
            details: parsed.error.flatten()
          }
        });
        return;
      }

      response.json({ data: await tokenService.updateToken(request.params.id, parsed.data) });
    } catch (error) {
      next(error);
    }
  });

  router.delete('/:id', async (request, response, next) => {
    try {
      await tokenService.deleteToken(
        request.params.id,
        typeof request.query.actor === 'string' ? request.query.actor : 'system'
      );
      response.status(204).send();
    } catch (error) {
      next(error);
    }
  });

  router.post('/:id/approve', async (request, response, next) => {
    try {
      const parsed = actorActionSchema.safeParse(request.body);
      if (!parsed.success) {
        response.status(400).json({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Approval payload is invalid',
            details: parsed.error.flatten()
          }
        });
        return;
      }
      response.json({
        data: await tokenService.approveToken(
          request.params.id,
          parsed.data.actor,
          parsed.data.changeNote
        )
      });
    } catch (error) {
      next(error);
    }
  });

  router.post('/:id/reject', async (request, response, next) => {
    try {
      const parsed = actorActionSchema.safeParse(request.body);
      if (!parsed.success) {
        response.status(400).json({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Rejection payload is invalid',
            details: parsed.error.flatten()
          }
        });
        return;
      }
      response.json({
        data: await tokenService.rejectToken(
          request.params.id,
          parsed.data.actor,
          parsed.data.changeNote
        )
      });
    } catch (error) {
      next(error);
    }
  });

  router.post('/:id/deprecate', async (request, response, next) => {
    try {
      const parsed = actorActionSchema.safeParse(request.body);
      if (!parsed.success) {
        response.status(400).json({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Deprecation payload is invalid',
            details: parsed.error.flatten()
          }
        });
        return;
      }
      response.json({
        data: await tokenService.deprecateToken(
          request.params.id,
          parsed.data.actor,
          parsed.data.changeNote
        )
      });
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id/versions', async (request, response, next) => {
    try {
      response.json({ data: await tokenService.listVersions(request.params.id) });
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id/audit', async (request, response, next) => {
    try {
      response.json({ data: await tokenService.listAuditEvents({ tokenId: request.params.id }) });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
