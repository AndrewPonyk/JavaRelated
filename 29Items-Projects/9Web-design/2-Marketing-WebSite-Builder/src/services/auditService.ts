import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";

export async function writeAuditLog(input: {
  organizationId: string;
  actorId?: string;
  action: string;
  targetType: string;
  targetId: string;
  metadata?: Record<string, unknown>;
}) {
  await prisma.auditLog.create({
    data: {
      organizationId: input.organizationId,
      actorId: input.actorId,
      action: input.action,
      targetType: input.targetType,
      targetId: input.targetId,
      metadata: input.metadata ? toInputJson(input.metadata) : undefined
    }
  });
}
