import type { Actor } from "@/lib/auth";
import { requireOrganizationRole } from "@/lib/auth";
import { NotFoundError } from "@/lib/errors";
import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";
import type { FigmaImportInput } from "@/lib/validation";
import type { PageSummary, SiteDocument } from "@/domain/site";
import { mapFigmaNodesToDocument } from "@/services/pageDocumentService";
import { siteService } from "@/services/siteService";
import { writeAuditLog } from "@/services/auditService";

function toPageSummary(page: {
  id: string;
  siteId: string;
  title: string;
  slug: string;
  version: number;
  document: unknown;
  updatedAt: Date;
}): PageSummary {
  return {
    id: page.id,
    siteId: page.siteId,
    title: page.title,
    slug: page.slug,
    version: page.version,
    document: page.document as SiteDocument,
    updatedAt: page.updatedAt.toISOString()
  };
}

export const figmaImportService = {
  async importSelection(actor: Actor, input: FigmaImportInput): Promise<PageSummary> {
    await requireOrganizationRole(actor.id, input.organizationId, "MARKETER");

    const site = input.siteId
      ? await prisma.site.findFirst({
          where: { id: input.siteId, organizationId: input.organizationId }
        })
      : await siteService.createSite(actor, {
          organizationId: input.organizationId,
          name: input.name,
          slug: input.name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "")
        });

    if (!site) {
      throw new NotFoundError("Site");
    }

    const siteId = "id" in site ? site.id : input.siteId;
    const document = mapFigmaNodesToDocument(input.nodes);

    const page = await prisma.page.create({
      data: {
        siteId: siteId as string,
        title: input.name,
        slug: `figma-${Date.now()}`,
        document: toInputJson(document)
      }
    });

    await writeAuditLog({
      organizationId: input.organizationId,
      actorId: actor.id,
      action: "figma.imported",
      targetType: "Page",
      targetId: page.id,
      metadata: { nodeCount: input.nodes.length }
    });

    return toPageSummary(page);
  }
};
