import { Prisma } from "@prisma/client";
import type { Actor } from "@/lib/auth";
import { requireOrganizationRole } from "@/lib/auth";
import { ConflictError, NotFoundError } from "@/lib/errors";
import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";
import type { CreatePageInput, UpdatePageInput } from "@/lib/validation";
import type { PageSummary, SiteDocument } from "@/domain/site";
import { createDefaultDocument, normalizeDocument } from "@/services/pageDocumentService";
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

async function getSiteForPageAccess(actor: Actor, siteId: string, role: "VIEWER" | "MARKETER") {
  const site = await prisma.site.findUnique({ where: { id: siteId } });

  if (!site) {
    throw new NotFoundError("Site");
  }

  await requireOrganizationRole(actor.id, site.organizationId, role);
  return site;
}

export const pageService = {
  async listPages(actor: Actor, siteId: string): Promise<PageSummary[]> {
    await getSiteForPageAccess(actor, siteId, "VIEWER");

    const pages = await prisma.page.findMany({
      where: { siteId },
      orderBy: { updatedAt: "desc" }
    });

    return pages.map(toPageSummary);
  },

  async createPage(actor: Actor, siteId: string, input: CreatePageInput): Promise<PageSummary> {
    const site = await getSiteForPageAccess(actor, siteId, "MARKETER");

    try {
      const page = await prisma.page.create({
        data: {
          siteId,
          title: input.title,
          slug: input.slug,
          document: toInputJson(input.document ? normalizeDocument(input.document) : createDefaultDocument())
        }
      });

      await writeAuditLog({
        organizationId: site.organizationId,
        actorId: actor.id,
        action: "page.created",
        targetType: "Page",
        targetId: page.id
      });

      return toPageSummary(page);
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2002"
      ) {
        throw new ConflictError("Page slug is already in use for this site");
      }

      throw error;
    }
  },

  async updatePage(
    actor: Actor,
    siteId: string,
    pageId: string,
    input: UpdatePageInput
  ): Promise<PageSummary> {
    const site = await getSiteForPageAccess(actor, siteId, "MARKETER");

    try {
      const page = await prisma.page.update({
        where: { id: pageId, siteId },
        data: {
          title: input.title,
          slug: input.slug,
          document: input.document ? toInputJson(normalizeDocument(input.document)) : undefined,
          version: input.document ? { increment: 1 } : undefined
        }
      });

      await writeAuditLog({
        organizationId: site.organizationId,
        actorId: actor.id,
        action: "page.updated",
        targetType: "Page",
        targetId: page.id
      });

      return toPageSummary(page);
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2025"
      ) {
        throw new NotFoundError("Page");
      }

      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2002"
      ) {
        throw new ConflictError("Page slug is already in use for this site");
      }

      throw error;
    }
  },

  async deletePage(actor: Actor, siteId: string, pageId: string): Promise<void> {
    const site = await getSiteForPageAccess(actor, siteId, "MARKETER");

    try {
      await prisma.page.delete({ where: { id: pageId, siteId } });
      await writeAuditLog({
        organizationId: site.organizationId,
        actorId: actor.id,
        action: "page.deleted",
        targetType: "Page",
        targetId: pageId
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2025"
      ) {
        throw new NotFoundError("Page");
      }

      throw error;
    }
  }
};
