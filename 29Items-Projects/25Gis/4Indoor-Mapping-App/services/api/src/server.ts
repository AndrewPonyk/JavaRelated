import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import pino from 'pino';
import compression from 'compression';

import { getEnv } from './config/env';
import { requireFirebaseAuth } from './middleware/auth.middleware';
import { errorMiddleware } from './middleware/error.middleware';
import { requireHttps } from './middleware/security.middleware';
import { beaconAnchorRouter } from './routes/beacon.routes';
import { floorRouter } from './routes/floor.routes';
import { heatmapCellRouter } from './routes/heatmap.routes';
import { poiRouter } from './routes/poi.routes';
import { positioningEventRouter } from './routes/positioning.routes';
import { routeRouter } from './routes/route.routes';
import { venueRouter } from './routes/venue.routes';
import { wifiFingerprintRouter } from './routes/wifi.routes';

const env = getEnv();
const logger = pino({
  level: env.LOG_LEVEL,
  redact: ['req.headers.authorization', 'authorization', '*.authorization'],
});

export function createApp() {
  const app = express();

  if (env.TRUST_PROXY) {
    app.set('trust proxy', 1);
  }

  app.use(requireHttps);
  app.use(helmet());
  app.use(
    cors({
      origin: env.CORS_ORIGINS.length === 0 ? false : env.CORS_ORIGINS,
    }),
  );
  app.use(compression());
  app.use(express.json({ limit: '1mb' }));
  app.use((req, res, next) => {
    const startedAt = Date.now();
    res.on('finish', () => {
      logger.info(
        {
          method: req.method,
          path: req.path,
          statusCode: res.statusCode,
          latencyMs: Date.now() - startedAt,
        },
        'request completed',
      );
    });
    next();
  });

  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  app.use('/api/venues', venueRouter);
  app.use('/api/floors', floorRouter);
  app.use('/api/pois', requireFirebaseAuth, poiRouter);
  app.use('/api/routes', requireFirebaseAuth, routeRouter);
  app.use('/api/beacon-anchors', requireFirebaseAuth, beaconAnchorRouter);
  app.use('/api/wifi-fingerprints', requireFirebaseAuth, wifiFingerprintRouter);
  app.use('/api/positioning-events', requireFirebaseAuth, positioningEventRouter);
  app.use('/api/heatmap-cells', requireFirebaseAuth, heatmapCellRouter);
  app.use(errorMiddleware);

  return app;
}

if (require.main === module) {
  createApp().listen(env.PORT, () => {
    logger.info({ port: env.PORT }, 'Indoor Mapping API started');
  });
}
