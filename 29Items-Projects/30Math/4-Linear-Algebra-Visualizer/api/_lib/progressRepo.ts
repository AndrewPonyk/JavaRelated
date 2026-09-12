import { Prisma, Topic as DbTopic } from '@prisma/client';

import { prisma } from './db';
import { nextMastery } from './mastery';
import type { AttemptInput, TopicSlug } from './schemas';

/**
 * Service/repository layer: routes handle HTTP, this module handles
 * persistence. The seam where a queue producer or caching would slot in
 * later (ARCHITECTURE §2.2).
 */

const TOPIC_TO_DB: Record<TopicSlug, DbTopic> = {
  vectors: DbTopic.VECTORS,
  'linear-combinations': DbTopic.LINEAR_COMBINATIONS,
  transformations: DbTopic.TRANSFORMATIONS,
  determinant: DbTopic.DETERMINANT,
  eigenvalues: DbTopic.EIGENVALUES,
};

const DB_TO_TOPIC = Object.fromEntries(
  Object.entries(TOPIC_TO_DB).map(([slug, db]) => [db, slug]),
) as Record<DbTopic, TopicSlug>;

export interface TopicProgressDto {
  topic: TopicSlug;
  mastery: number;
  attempts: number;
}

/**
 * Records one attempt and recomputes mastery SERVER-SIDE inside a transaction —
 * persisted mastery never trusts the client (ARCHITECTURE §2.5). Returns the
 * authoritative progress row so the client can reconcile its optimistic value.
 *
 * IDEMPOTENT on `attemptId`: the client's offline queue delivers at-least-once
 * (a response can be lost after the write committed), so a replayed attempt
 * returns the current row without counting again.
 */
export async function recordAttempt(input: AttemptInput): Promise<TopicProgressDto> {
  const topic = TOPIC_TO_DB[input.topic];

  try {
    return await prisma.$transaction(async (tx) => {
      const user = await tx.user.upsert({
        where: { deviceId: input.deviceId },
        update: {},
        create: { deviceId: input.deviceId },
      });

      const replayed = await tx.exerciseAttempt.findUnique({
        where: { clientAttemptId: input.attemptId },
      });
      if (replayed) {
        const row = await tx.topicProgress.findUnique({
          where: { userId_topic: { userId: user.id, topic } },
        });
        return {
          topic: input.topic,
          mastery: row?.mastery ?? 0,
          attempts: row?.attemptsCount ?? 0,
        };
      }

      await tx.exerciseAttempt.create({
        data: {
          userId: user.id,
          topic,
          difficulty: input.difficulty,
          seed: input.seed,
          correct: input.correct,
          durationMs: input.durationMs,
          clientAttemptId: input.attemptId,
        },
      });

      const existing = await tx.topicProgress.findUnique({
        where: { userId_topic: { userId: user.id, topic } },
      });
      const mastery = nextMastery(existing?.mastery ?? 0, input.correct);

      const row = await tx.topicProgress.upsert({
        where: { userId_topic: { userId: user.id, topic } },
        update: { mastery, attemptsCount: { increment: 1 } },
        create: { userId: user.id, topic, mastery, attemptsCount: 1 },
      });

      return { topic: input.topic, mastery: row.mastery, attempts: row.attemptsCount };
    });
  } catch (error) {
    // Two identical requests racing past the replay check: the second create
    // hits the unique constraint. Treat it as the replay it is.
    if (isUniqueViolation(error)) {
      const progress = await getProgress(input.deviceId);
      const row = progress.find((p) => p.topic === input.topic);
      return row ?? { topic: input.topic, mastery: 0, attempts: 0 };
    }
    throw error;
  }
}

const isUniqueViolation = (error: unknown): boolean =>
  error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002';

/** Unknown devices get an empty list — the client merges with local defaults. */
export async function getProgress(deviceId: string): Promise<TopicProgressDto[]> {
  const user = await prisma.user.findUnique({
    where: { deviceId },
    include: { progress: true },
  });
  if (!user) return [];

  return user.progress.map((row) => ({
    topic: DB_TO_TOPIC[row.topic],
    mastery: row.mastery,
    attempts: row.attemptsCount,
  }));
}
