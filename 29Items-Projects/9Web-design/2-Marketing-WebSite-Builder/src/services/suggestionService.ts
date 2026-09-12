import type { Actor } from "@/lib/auth";
import { requireOrganizationRole } from "@/lib/auth";
import { NotFoundError } from "@/lib/errors";
import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";
import type { LayoutSuggestionSummary, SiteDocument } from "@/domain/site";
import { buildSuggestionDocument } from "@/services/pageDocumentService";
import { analyticsService } from "@/services/analyticsService";
import { writeAuditLog } from "@/services/auditService";

function toSuggestionSummary(suggestion: {
  id: string;
  siteId: string;
  title: string;
  rationale: string;
  confidence: number;
  proposedDocument: unknown;
  createdAt: Date;
}): LayoutSuggestionSummary {
  return {
    id: suggestion.id,
    siteId: suggestion.siteId,
    title: suggestion.title,
    rationale: suggestion.rationale,
    confidence: suggestion.confidence,
    proposedDocument: suggestion.proposedDocument as SiteDocument,
    createdAt: suggestion.createdAt.toISOString()
  };
}

export const suggestionService = {
  async listSuggestions(actor: Actor, siteId: string): Promise<LayoutSuggestionSummary[]> {
    const site = await prisma.site.findUnique({ where: { id: siteId } });

    if (!site) {
      throw new NotFoundError("Site");
    }

    await requireOrganizationRole(actor.id, site.organizationId, "VIEWER");

    const suggestions = await prisma.layoutSuggestion.findMany({
      where: { siteId },
      orderBy: { createdAt: "desc" }
    });

    return suggestions.map(toSuggestionSummary);
  },

  async generateSuggestion(actor: Actor, siteId: string): Promise<LayoutSuggestionSummary> {
    const site = await prisma.site.findUnique({
      where: { id: siteId },
      include: { pages: { orderBy: { createdAt: "asc" }, take: 1 } }
    });

    if (!site || !site.pages[0]) {
      throw new NotFoundError("Site");
    }

    await requireOrganizationRole(actor.id, site.organizationId, "MARKETER");

    const analytics = await analyticsService.getSummary(actor, site.organizationId, site.id);
    const proposedDocument = buildSuggestionDocument(
      site.pages[0].document as unknown as SiteDocument,
      analytics.conversionRate
    );

    const suggestion = await prisma.layoutSuggestion.create({
      data: {
        siteId,
        title:
          analytics.conversionRate < 0.08
            ? "Add trust-building proof near the hero"
            : "Reduce friction with an FAQ after the CTA",
        rationale:
          analytics.conversionRate < 0.08
            ? "Low conversion pages often benefit from social proof before asking for a form submission."
            : "FAQ content can handle objections without distracting from the primary CTA.",
        confidence: analytics.totalEvents === 0 ? 0.62 : Math.min(0.92, 0.7 + analytics.totalEvents / 1000),
        proposedDocument: toInputJson(proposedDocument)
      }
    });

    await writeAuditLog({
      organizationId: site.organizationId,
      actorId: actor.id,
      action: "suggestion.generated",
      targetType: "LayoutSuggestion",
      targetId: suggestion.id
    });

    return toSuggestionSummary(suggestion);
  }
};
