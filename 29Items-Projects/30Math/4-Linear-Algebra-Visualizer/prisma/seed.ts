import { existsSync } from 'node:fs';

import { PrismaClient, Topic } from '@prisma/client';

// Prisma CLI loads .env automatically but Prisma Client does not — this script
// runs under plain tsx, so load it here (no-op when env vars come from elsewhere).
if (existsSync('.env')) process.loadEnvFile('.env');

/** Local development seed: one demo device with a bit of history. */
const prisma = new PrismaClient();

async function main(): Promise<void> {
  const demo = await prisma.user.upsert({
    where: { deviceId: 'demo-device-00000000' },
    update: {},
    create: { deviceId: 'demo-device-00000000' },
  });

  await prisma.topicProgress.upsert({
    where: { userId_topic: { userId: demo.id, topic: Topic.VECTORS } },
    update: { mastery: 0.85, attemptsCount: 12 },
    create: { userId: demo.id, topic: Topic.VECTORS, mastery: 0.85, attemptsCount: 12 },
  });

  await prisma.exerciseAttempt.create({
    data: {
      userId: demo.id,
      topic: Topic.VECTORS,
      difficulty: 2,
      seed: 42,
      correct: true,
      durationMs: 8_500,
    },
  });

  console.log(`Seeded demo user ${demo.id}`);
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
