import { Prisma } from "@prisma/client";
import type { Actor } from "@/lib/auth";
import { requireOrganizationRole } from "@/lib/auth";
import { NotFoundError, ValidationError } from "@/lib/errors";
import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";
import type { CreateExperimentInput, UpdateExperimentInput } from "@/lib/validation";
import type { ExperimentSummary, SiteDocument } from "@/domain/site";
import { normalizeDocument } from "@/services/pageDocumentService";
import { writeAuditLog } from "@/services/auditService";

function assertTrafficShare(variants: Array<{ trafficShare: number }>) {
  const total = variants.reduce((sum, variant) => sum + variant.trafficShare, 0);

  if (total !== 100) {
    throw new ValidationError("Experiment variant traffic share must total 100", {
      total
    });
  }
}

function toExperimentSummary(experiment: {
  id: string;
  organizationId: string;
  siteId: string;
  name: string;
  status: ExperimentSummary["status"];
  variants: Array<{
    id: string;
    name: string;
    trafficShare: number;
    pageDocument: unknown;
  }>;
}): ExperimentSummary {
  return {
    id: experiment.id,
    organizationId: experiment.organizationId,
    siteId: experiment.siteId,
    name: experiment.name,
    status: experiment.status,
    variants: experiment.variants.map((variant) => ({
      id: variant.id,
      name: variant.name,
      trafficShare: variant.trafficShare,
      pageDocument: variant.pageDocument as SiteDocument
    }))
  };
}

export const experimentService = {
  async listExperiments(actor: Actor, organizationId: string): Promise<ExperimentSummary[]> {
    await requireOrganizationRole(actor.id, organizationId, "VIEWER");

    const experiments = await prisma.experiment.findMany({
      where: { organizationId },
      include: { variants: true },
      orderBy: { updatedAt: "desc" }
    });

    return experiments.map(toExperimentSummary);
  },

  async createExperiment(
    actor: Actor,
    input: CreateExperimentInput
  ): Promise<ExperimentSummary> {
    assertTrafficShare(input.variants);
    await requireOrganizationRole(actor.id, input.organizationId, "MARKETER");

    const site = await prisma.site.findFirst({
      where: { id: input.siteId, organizationId: input.organizationId }
    });

    if (!site) {
      throw new NotFoundError("Site");
    }

    const experiment = await prisma.experiment.create({
      data: {
        organizationId: input.organizationId,
        siteId: input.siteId,
        name: input.name,
        variants: {
          create: input.variants.map((variant) => ({
            name: variant.name,
            trafficShare: variant.trafficShare,
            pageDocument: toInputJson(normalizeDocument(variant.pageDocument))
          }))
        }
      },
      include: { variants: true }
    });

    await writeAuditLog({
      organizationId: input.organizationId,
      actorId: actor.id,
      action: "experiment.created",
      targetType: "Experiment",
      targetId: experiment.id
    });

    return toExperimentSummary(experiment);
  },

  async updateExperiment(
    actor: Actor,
    id: string,
    input: UpdateExperimentInput
  ): Promise<ExperimentSummary> {
    const existing = await prisma.experiment.findUnique({
      where: { id },
      include: { variants: true }
    });

    if (!existing) {
      throw new NotFoundError("Experiment");
    }

    await requireOrganizationRole(actor.id, existing.organizationId, "MARKETER");

    if (input.variants) {
      assertTrafficShare(input.variants);
    }

    const experiment = await prisma.$transaction(async (transaction) => {
      if (input.variants) {
        await transaction.experimentVariant.deleteMany({
          where: { experimentId: id }
        });
      }

      return transaction.experiment.update({
        where: { id },
        data: {
          name: input.name,
          status: input.status,
          variants: input.variants
            ? {
                create: input.variants.map((variant) => ({
                  name: variant.name,
                  trafficShare: variant.trafficShare,
                  pageDocument: toInputJson(normalizeDocument(variant.pageDocument))
                }))
              }
            : undefined
        },
        include: { variants: true }
      });
    });

    await writeAuditLog({
      organizationId: existing.organizationId,
      actorId: actor.id,
      action: "experiment.updated",
      targetType: "Experiment",
      targetId: id,
      metadata: { status: input.status }
    });

    return toExperimentSummary(experiment);
  },

  async deleteExperiment(actor: Actor, id: string): Promise<void> {
    const existing = await prisma.experiment.findUnique({ where: { id } });

    if (!existing) {
      throw new NotFoundError("Experiment");
    }

    await requireOrganizationRole(actor.id, existing.organizationId, "MARKETER");

    try {
      await prisma.experiment.delete({ where: { id } });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2025"
      ) {
        throw new NotFoundError("Experiment");
      }

      throw error;
    }
  }
};
