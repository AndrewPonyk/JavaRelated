import { randomUUID } from 'node:crypto';

import { SignJWT, jwtVerify } from 'jose';
import { z } from 'zod';

const TOKEN_ISSUER = 'webassembly-image-editor';
const TOKEN_AUDIENCE = 'webassembly-image-editor-api';
const tokenSubjectSchema = z.string().uuid();
const tokenNameSchema = z.string().trim().min(1).max(60);

export interface AuthenticatedUser {
  readonly id: string;
  readonly displayName: string;
}

function secretBytes(secret: string): Uint8Array {
  return new TextEncoder().encode(secret);
}

export class AuthenticationError extends Error {
  constructor(message = 'A valid bearer token is required.') {
    super(message);
    this.name = 'AuthenticationError';
  }
}

export class SessionTokenService {
  constructor(private readonly secret: string) {}

  async create(user: AuthenticatedUser): Promise<string> {
    return new SignJWT({ name: user.displayName })
      .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
      .setSubject(user.id)
      .setIssuer(TOKEN_ISSUER)
      .setAudience(TOKEN_AUDIENCE)
      .setJti(randomUUID())
      .setIssuedAt()
      .setExpirationTime('7d')
      .sign(secretBytes(this.secret));
  }

  async verify(authorization: string | undefined): Promise<AuthenticatedUser> {
    const token = authorization?.match(/^Bearer\s+(.+)$/i)?.[1];
    if (!token) throw new AuthenticationError();
    try {
      const { payload } = await jwtVerify(token, secretBytes(this.secret), {
        algorithms: ['HS256'],
        issuer: TOKEN_ISSUER,
        audience: TOKEN_AUDIENCE,
      });
      const id = tokenSubjectSchema.safeParse(payload.sub);
      const displayName = tokenNameSchema.safeParse(payload.name);
      if (!id.success || !displayName.success) {
        throw new AuthenticationError('The bearer token has an invalid payload.');
      }
      return { id: id.data, displayName: displayName.data };
    } catch (error) {
      if (error instanceof AuthenticationError) throw error;
      throw new AuthenticationError('The bearer token is expired or invalid.');
    }
  }
}
