import type { NextFunction, Request, Response } from 'express';
import admin from 'firebase-admin';

import { getEnv } from '../config/env';
import { ApiError } from './error.middleware';

let firebaseInitialized = false;

function ensureFirebase() {
  if (firebaseInitialized || admin.apps.length > 0) {
    firebaseInitialized = true;
    return;
  }

  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n');

  if (!projectId || !clientEmail || !privateKey) {
    throw new ApiError(500, 'firebase_not_configured', 'Firebase credentials are not configured.');
  }

  admin.initializeApp({
    credential: admin.credential.cert({
      projectId,
      clientEmail,
      privateKey,
    }),
  });
  firebaseInitialized = true;
}

export async function requireFirebaseAuth(req: Request, _res: Response, next: NextFunction) {
  if (!getEnv().REQUIRE_FIREBASE_AUTH) {
    next();
    return;
  }

  try {
    const authorization = req.header('authorization') ?? '';
    const match = authorization.match(/^Bearer\s+(.+)$/i);
    if (!match) {
      throw new ApiError(401, 'missing_token', 'Bearer token is required.');
    }

    ensureFirebase();
    await admin.auth().verifyIdToken(match[1]);
    next();
  } catch (error) {
    if (error instanceof ApiError) {
      next(error);
      return;
    }

    next(new ApiError(401, 'invalid_token', 'Bearer token is invalid.'));
  }
}
