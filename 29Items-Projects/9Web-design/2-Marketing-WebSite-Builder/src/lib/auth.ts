import type { Role } from "@/domain/site";
import { prisma } from "@/lib/db";
import { ForbiddenError, UnauthorizedError } from "@/lib/errors";

export interface Actor {
  id: string;
  email: string;
  name: string;
}

const roleRank: Record<Role, number> = {
  VIEWER: 1,
  REVIEWER: 2,
  MARKETER: 3,
  DEVELOPER: 4,
  OWNER: 5
};

export async function getActor(request: Request): Promise<Actor> {
  const email =
    request.headers.get("x-user-email") ??
    process.env.DEMO_USER_EMAIL ??
    "demo@builder.local";
  const name = request.headers.get("x-user-name") ?? "Demo Marketer";

  if (!email.includes("@")) {
    throw new UnauthorizedError("A valid x-user-email header is required");
  }

  const user = await prisma.user.upsert({
    where: { email },
    update: { name },
    create: { email, name }
  });

  return {
    id: user.id,
    email: user.email,
    name: user.name
  };
}

export async function requireOrganizationRole(
  actorId: string,
  organizationId: string,
  minimumRole: Role
) {
  const membership = await prisma.membership.findUnique({
    where: {
      userId_organizationId: {
        userId: actorId,
        organizationId
      }
    }
  });

  if (!membership) {
    throw new ForbiddenError("You are not a member of this organization");
  }

  if (roleRank[membership.role] < roleRank[minimumRole]) {
    throw new ForbiddenError(`The ${minimumRole} role is required`);
  }

  return membership;
}

export async function ensureOwnerMembership(actorId: string, organizationId: string) {
  await prisma.membership.upsert({
    where: {
      userId_organizationId: {
        userId: actorId,
        organizationId
      }
    },
    update: { role: "OWNER" },
    create: {
      userId: actorId,
      organizationId,
      role: "OWNER"
    }
  });
}
