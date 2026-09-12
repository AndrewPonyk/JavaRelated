import { PrismaClient } from '@prisma/client';

/**
 * Serverless-safe Prisma singleton: warm invocations of the same instance
 * reuse one client instead of exhausting Postgres connections
 * (TECH-NOTES §3.6 pitfall 6). Also pair with a POOLED connection string.
 */
const globalForPrisma = globalThis as unknown as { prisma?: PrismaClient };

export const prisma: PrismaClient = (globalForPrisma.prisma ??= new PrismaClient());
