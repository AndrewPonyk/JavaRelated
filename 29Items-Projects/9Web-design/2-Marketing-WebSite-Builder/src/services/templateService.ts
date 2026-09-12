import { Prisma } from "@prisma/client";
import type { Actor } from "@/lib/auth";
import { requireOrganizationRole } from "@/lib/auth";
import { NotFoundError } from "@/lib/errors";
import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";
import type { CreateTemplateInput, UpdateTemplateInput } from "@/lib/validation";
import type { BlockTemplateSummary, SiteDocument } from "@/domain/site";
import { normalizeDocument } from "@/services/pageDocumentService";
import { writeAuditLog } from "@/services/auditService";

function toTemplateSummary(template: {
  id: string;
  organizationId: string;
  name: string;
  category: BlockTemplateSummary["category"];
  document: unknown;
}): BlockTemplateSummary {
  return {
    id: template.id,
    organizationId: template.organizationId,
    name: template.name,
    category: template.category,
    document: template.document as SiteDocument
  };
}

export const templateService = {
  async listTemplates(actor: Actor, organizationId: string): Promise<BlockTemplateSummary[]> {
    await requireOrganizationRole(actor.id, organizationId, "VIEWER");

    const templates = await prisma.blockTemplate.findMany({
      where: { organizationId },
      orderBy: [{ category: "asc" }, { name: "asc" }]
    });

    return templates.map(toTemplateSummary);
  },

  async createTemplate(
    actor: Actor,
    input: CreateTemplateInput
  ): Promise<BlockTemplateSummary> {
    await requireOrganizationRole(actor.id, input.organizationId, "DEVELOPER");

    const template = await prisma.blockTemplate.create({
      data: {
        organizationId: input.organizationId,
        name: input.name,
        category: input.category,
        document: toInputJson(normalizeDocument(input.document))
      }
    });

    await writeAuditLog({
      organizationId: input.organizationId,
      actorId: actor.id,
      action: "template.created",
      targetType: "BlockTemplate",
      targetId: template.id
    });

    return toTemplateSummary(template);
  },

  async updateTemplate(
    actor: Actor,
    id: string,
    input: UpdateTemplateInput
  ): Promise<BlockTemplateSummary> {
    const existing = await prisma.blockTemplate.findUnique({ where: { id } });

    if (!existing) {
      throw new NotFoundError("Block template");
    }

    await requireOrganizationRole(actor.id, existing.organizationId, "DEVELOPER");

    const template = await prisma.blockTemplate.update({
      where: { id },
      data: {
        name: input.name,
        category: input.category,
        document: input.document ? toInputJson(normalizeDocument(input.document)) : undefined
      }
    });

    await writeAuditLog({
      organizationId: template.organizationId,
      actorId: actor.id,
      action: "template.updated",
      targetType: "BlockTemplate",
      targetId: template.id
    });

    return toTemplateSummary(template);
  },

  async deleteTemplate(actor: Actor, id: string): Promise<void> {
    const existing = await prisma.blockTemplate.findUnique({ where: { id } });

    if (!existing) {
      throw new NotFoundError("Block template");
    }

    await requireOrganizationRole(actor.id, existing.organizationId, "DEVELOPER");

    try {
      await prisma.blockTemplate.delete({ where: { id } });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2025"
      ) {
        throw new NotFoundError("Block template");
      }

      throw error;
    }
  }
};
