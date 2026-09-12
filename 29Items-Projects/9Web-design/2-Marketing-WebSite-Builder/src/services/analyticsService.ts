import type { Actor } from "@/lib/auth";
import { requireOrganizationRole } from "@/lib/auth";
import { NotFoundError } from "@/lib/errors";
import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";
import type { ConversionEventInput } from "@/lib/validation";
import type { AnalyticsSummary } from "@/domain/site";
import { writeAuditLog } from "@/services/auditService";

function countBy<T extends string>(items: T[]): Record<string, number> {
  return items.reduce<Record<string, number>>((counts, item) => {
    counts[item || "unknown"] = (counts[item || "unknown"] ?? 0) + 1;
    return counts;
  }, {});
}

export const analyticsService = {
  async recordEvent(input: ConversionEventInput) {
    const site = await prisma.site.findFirst({
      where: { id: input.siteId, organizationId: input.organizationId }
    });

    if (!site) {
      throw new NotFoundError("Site");
    }

    const event = await prisma.conversionEvent.create({
      data: {
        organizationId: input.organizationId,
        siteId: input.siteId,
        pageSlug: input.pageSlug,
        eventName: input.eventName,
        source: input.source,
        metadata: input.metadata ? toInputJson(input.metadata) : undefined
      }
    });

    await writeAuditLog({
      organizationId: input.organizationId,
      action: "conversion.recorded",
      targetType: "ConversionEvent",
      targetId: event.id,
      metadata: { eventName: input.eventName }
    });

    return {
      id: event.id,
      createdAt: event.createdAt.toISOString()
    };
  },

  async getSummary(actor: Actor, organizationId: string, siteId?: string): Promise<AnalyticsSummary> {
    await requireOrganizationRole(actor.id, organizationId, "VIEWER");

    const events = await prisma.conversionEvent.findMany({
      where: {
        organizationId,
        siteId
      }
    });

    const totalEvents = events.length;
    const conversionEvents = events.filter((event) =>
      ["signup", "lead", "purchase", "form_submit"].includes(event.eventName.toLowerCase())
    ).length;

    return {
      totalEvents,
      byEventName: countBy(events.map((event) => event.eventName)),
      bySource: countBy(events.map((event) => event.source ?? "unknown")),
      conversionRate: totalEvents === 0 ? 0 : conversionEvents / totalEvents
    };
  }
};
