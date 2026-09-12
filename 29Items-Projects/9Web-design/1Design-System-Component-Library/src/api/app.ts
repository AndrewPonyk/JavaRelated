import compression from 'compression';
import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import { createTokenSetRouter } from './token-sets.controller';
import { createTokenRouter } from './tokens.controller';
import { errorHandler } from './error-handler';
import type { TokenService } from '../services/token.service';
import { getCorsOrigin, requireApiKey, requireHttps, requireJsonMutations } from './security';

export function createApp(tokenService: TokenService): express.Express {
  const app = express();

  app.set('trust proxy', process.env.TRUST_PROXY === 'true' ? 1 : false);
  app.use(
    helmet({
      contentSecurityPolicy: false
    })
  );
  app.use(cors({ origin: getCorsOrigin() }));
  app.use(compression());
  app.use(requireHttps());
  app.use(
    rateLimit({
      windowMs: Number(process.env.RATE_LIMIT_WINDOW_MS ?? 60_000),
      limit: Number(process.env.RATE_LIMIT_MAX ?? 300),
      standardHeaders: true,
      legacyHeaders: false
    })
  );
  app.use(express.json({ limit: '1mb' }));
  app.use(requireJsonMutations());
  app.use(requireApiKey());
  app.use(express.static('public'));

  app.get('/health', (_request, response) => {
    response.json({ status: 'ok' });
  });

  app.get('/api/status', async (_request, response, next) => {
    try {
      response.json(await tokenService.getStatusSummary());
    } catch (error) {
      next(error);
    }
  });

  app.use('/api/token-sets', createTokenSetRouter(tokenService));
  app.use('/api/tokens', createTokenRouter(tokenService));
  app.use(errorHandler);

  return app;
}
