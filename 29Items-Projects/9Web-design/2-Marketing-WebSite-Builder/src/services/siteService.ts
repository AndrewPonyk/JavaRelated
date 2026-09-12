import { Prisma } from "@prisma/client";
import type { Actor } from "@/lib/auth";
import { requireOrganizationRole } from "@/lib/auth";
import { ConflictError, NotFoundError } from "@/lib/errors";
import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";
import type { CreateSiteInput, UpdateSiteInput } from "@/lib/validation";
import type { SiteSummary } from "@/domain/site";
import { createDefaultDocument } from "@/services/pageDocumentService";
import { writeAuditLog } from "@/services/auditService";

function toSiteSummary(site: {
  id: string;
  organizationId: string;
  name: string;
  slug: string;
  status: SiteSummary["status"];
  updatedAt: Date;
  publishedAt: Date | null;
  _count?: { pages: number };
}): SiteSummary {
  return {
    id: site.id,
    organizationId: site.organizationId,
    name: site.name,
    slug: site.slug,
    status: site.status,
    pageCount: site._count?.pages ?? 0,
    updatedAt: site.updatedAt.toISOString(),
    publishedAt: site.publishedAt?.toISOString() ?? null
  };
}

export const siteService = {
  async listSites(actor: Actor, organizationId?: string): Promise<SiteSummary[]> {
    if (organizationId) {
      await requireOrganizationRole(actor.id, organizationId, "VIEWER");
    }

    const memberships = organizationId
      ? [{ organizationId }]
      : await prisma.membership.findMany({
          where: { userId: actor.id },
          select: { organizationId: true }
        });

    const sites = await prisma.site.findMany({
      where: { organizationId: { in: memberships.map((item) => item.organizationId) } },
      orderBy: { updatedAt: "desc" },
      include: { _count: { select: { pages: true } } }
    });

    return sites.map(toSiteSummary);
  },

  async getSite(actor: Actor, id: string): Promise<SiteSummary> {
    const site = await prisma.site.findUnique({
      where: { id },
      include: { _count: { select: { pages: true } } }
    });

    if (!site) {
      throw new NotFoundError("Site");
    }

    await requireOrganizationRole(actor.id, site.organizationId, "VIEWER");
    return toSiteSummary(site);
  },

  async createSite(actor: Actor, input: CreateSiteInput): Promise<SiteSummary> {
    await requireOrganizationRole(actor.id, input.organizationId, "MARKETER");

    try {
      const site = await prisma.site.create({
        data: {
          organizationId: input.organizationId,
          name: input.name,
          slug: input.slug,
          status: "DRAFT",
          pages: {
            create: {
              title: "Home",
              slug: "home",
              document: toInputJson(createDefaultDocument())
            }
          }
        },
        include: { _count: { select: { pages: true } } }
      });

      await writeAuditLog({
        organizationId: input.organizationId,
        actorId: actor.id,
        action: "site.created",
        targetType: "Site",
        targetId: site.id
      });

      return toSiteSummary(site);
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2002"
      ) {
        throw new ConflictError("Site slug is already in use for this organization");
      }

      throw error;
    }
  },

  async updateSite(actor: Actor, id: string, input: UpdateSiteInput): Promise<SiteSummary> {
    const existing = await prisma.site.findUnique({ where: { id } });

    if (!existing) {
      throw new NotFoundError("Site");
    }

    await requireOrganizationRole(actor.id, existing.organizationId, "MARKETER");

    try {
      const site = await prisma.site.update({
        where: { id },
        data: {
          ...input,
          publishedAt: input.status === "PUBLISHED" ? new Date() : undefined
        },
        include: { _count: { select: { pages: true } } }
      });

      await writeAuditLog({
        organizationId: site.organizationId,
        actorId: actor.id,
        action: "site.updated",
        targetType: "Site",
        targetId: id,
        metadata: input
      });

      return toSiteSummary(site);
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2002"
      ) {
        throw new ConflictError("Site slug is already in use for this organization");
      }

      throw error;
    }
  },

  async deleteSite(actor: Actor, id: string): Promise<void> {
    const existing = await prisma.site.findUnique({ where: { id } });

    if (!existing) {
      throw new NotFoundError("Site");
    }

    await requireOrganizationRole(actor.id, existing.organizationId, "DEVELOPER");
    await prisma.site.delete({ where: { id } });
  }
};
