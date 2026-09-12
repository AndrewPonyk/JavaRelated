-- AlterTable
ALTER TABLE "ExerciseAttempt" ADD COLUMN     "clientAttemptId" TEXT;

-- CreateIndex
CREATE UNIQUE INDEX "ExerciseAttempt_clientAttemptId_key" ON "ExerciseAttempt"("clientAttemptId");

