import { Prisma } from "@prisma/client";
import type { Actor } from "@/lib/auth";
import { ensureOwnerMembership, requireOrganizationRole } from "@/lib/auth";
import { ConflictError, NotFoundError } from "@/lib/errors";
import { prisma } from "@/lib/db";
import type {
  CreateOrganizationInput,
  UpdateOrganizationInput
} from "@/lib/validation";
import type { OrganizationSummary } from "@/domain/site";
import { writeAuditLog } from "@/services/auditService";

function toOrganizationSummary(input: {
  id: string;
  name: string;
  slug: string;
  role: OrganizationSummary["role"];
}): OrganizationSummary {
  return input;
}

export const organizationService = {
  async listOrganizations(actor: Actor): Promise<OrganizationSummary[]> {
    const memberships = await prisma.membership.findMany({
      where: { userId: actor.id },
      include: { organization: true },
      orderBy: { organization: { updatedAt: "desc" } }
    });

    return memberships.map((membership) =>
      toOrganizationSummary({
        id: membership.organization.id,
        name: membership.organization.name,
        slug: membership.organization.slug,
        role: membership.role
      })
    );
  },

  async createOrganization(
    actor: Actor,
    input: CreateOrganizationInput
  ): Promise<OrganizationSummary> {
    try {
      const organization = await prisma.organization.create({
        data: {
          name: input.name,
          slug: input.slug
        }
      });

      await ensureOwnerMembership(actor.id, organization.id);
      await writeAuditLog({
        organizationId: organization.id,
        actorId: actor.id,
        action: "organization.created",
        targetType: "Organization",
        targetId: organization.id
      });

      return toOrganizationSummary({
        id: organization.id,
        name: organization.name,
        slug: organization.slug,
        role: "OWNER"
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2002"
      ) {
        throw new ConflictError("Organization slug is already in use");
      }

      throw error;
    }
  },

  async getOrganization(actor: Actor, id: string): Promise<OrganizationSummary> {
    const membership = await requireOrganizationRole(actor.id, id, "VIEWER");
    const organization = await prisma.organization.findUnique({ where: { id } });

    if (!organization) {
      throw new NotFoundError("Organization");
    }

    return toOrganizationSummary({
      id: organization.id,
      name: organization.name,
      slug: organization.slug,
      role: membership.role
    });
  },

  async updateOrganization(
    actor: Actor,
    id: string,
    input: UpdateOrganizationInput
  ): Promise<OrganizationSummary> {
    await requireOrganizationRole(actor.id, id, "OWNER");

    try {
      const organization = await prisma.organization.update({
        where: { id },
        data: input
      });

      await writeAuditLog({
        organizationId: id,
        actorId: actor.id,
        action: "organization.updated",
        targetType: "Organization",
        targetId: id,
        metadata: input
      });

      return toOrganizationSummary({
        id: organization.id,
        name: organization.name,
        slug: organization.slug,
        role: "OWNER"
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2025"
      ) {
        throw new NotFoundError("Organization");
      }

      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2002"
      ) {
        throw new ConflictError("Organization slug is already in use");
      }

      throw error;
    }
  },

  async deleteOrganization(actor: Actor, id: string): Promise<void> {
    await requireOrganizationRole(actor.id, id, "OWNER");

    try {
      await prisma.organization.delete({ where: { id } });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2025"
      ) {
        throw new NotFoundError("Organization");
      }

      throw error;
    }
  }
};
