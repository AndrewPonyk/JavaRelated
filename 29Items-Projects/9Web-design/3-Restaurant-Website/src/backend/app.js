import cors from 'cors';
import compression from 'compression';
import express from 'express';
import rateLimit from 'express-rate-limit';
import helmet from 'helmet';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createReservationController, sendExpressResult } from './controllers/reservationController.js';
import { createReservationService } from './services/reservationService.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

export function createApp({ service = createReservationService(), serveStatic = true } = {}) {
  const app = express();
  const controller = createReservationController({ service });

  app.disable('x-powered-by');
  app.use(compression());
  app.use(
    helmet({
      contentSecurityPolicy: {
        directives: {
          defaultSrc: ["'self'"],
          scriptSrc: [
            "'self'",
            "'unsafe-inline'",
            'https://cdn.tailwindcss.com',
            'https://cdn.jsdelivr.net',
            'https://unpkg.com'
          ],
          styleSrc: ["'self'", "'unsafe-inline'", 'https://cdn.jsdelivr.net'],
          imgSrc: ["'self'", 'data:'],
          mediaSrc: ["'self'"],
          connectSrc: ["'self'"],
          fontSrc: ["'self'", 'data:'],
          objectSrc: ["'none'"],
          baseUri: ["'self'"],
          frameAncestors: ["'none'"]
        }
      },
      crossOriginEmbedderPolicy: false
    })
  );
  app.use(cors({ origin: resolveCorsOrigin() }));
  app.use(express.json({ limit: '32kb' }));

  const apiLimiter = rateLimit({
    windowMs: 60_000,
    limit: Number(process.env.API_RATE_LIMIT_PER_MINUTE || 60),
    standardHeaders: true,
    legacyHeaders: false,
    message: {
      ok: false,
      error: 'Too many reservation requests. Please try again shortly.',
      code: 'rate_limited',
      details: []
    }
  });

  app.get('/health', (_req, res) => {
    res.json({ ok: true, service: 'restaurant-website' });
  });

  app.use('/api', apiLimiter);
  app.use('/api/reservations', requireJsonForWrites);

  app.get('/api/reservations', async (req, res) => {
    sendExpressResult(res, await controller.handle({ method: 'GET', query: req.query }));
  });

  app.post('/api/reservations', async (req, res) => {
    sendExpressResult(res, await controller.handle({ method: 'POST', body: req.body }));
  });

  app.get('/api/reservations/:id', async (req, res) => {
    sendExpressResult(res, await controller.handle({ method: 'GET', id: req.params.id }));
  });

  app.put('/api/reservations/:id', async (req, res) => {
    sendExpressResult(res, await controller.handle({ method: 'PUT', id: req.params.id, body: req.body }));
  });

  app.delete('/api/reservations/:id', async (req, res) => {
    sendExpressResult(res, await controller.handle({ method: 'DELETE', id: req.params.id }));
  });

  if (serveStatic) {
    const distDir = path.join(root, 'dist');
    app.use(express.static(distDir, { index: false, maxAge: '1h' }));
    app.get(/.*/, (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  }

  app.use((error, _req, res, next) => {
    if (error instanceof SyntaxError && error.status === 400 && 'body' in error) {
      res.status(400).json({
        ok: false,
        error: 'Request body must be valid JSON.',
        code: 'invalid_json',
        details: []
      });
      return;
    }

    next(error);
  });

  return app;
}

function resolveCorsOrigin() {
  const origin = process.env.CORS_ORIGIN;
  if (!origin || origin === '*') return true;
  return origin.split(',').map((item) => item.trim());
}

function requireJsonForWrites(req, res, next) {
  if (['POST', 'PUT'].includes(req.method) && !req.is('application/json')) {
    res.status(415).json({
      ok: false,
      error: 'Content-Type must be application/json.',
      code: 'unsupported_media_type',
      details: []
    });
    return;
  }

  next();
}
