export type Role = "OWNER" | "DEVELOPER" | "MARKETER" | "REVIEWER" | "VIEWER";
export type SiteStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type BlockCategory =
  | "HERO"
  | "CTA"
  | "PRICING"
  | "TESTIMONIALS"
  | "FORM"
  | "FAQ";
export type ExperimentStatus = "DRAFT" | "RUNNING" | "PAUSED" | "COMPLETED";

export interface BuilderBlock {
  id: string;
  type: BlockCategory | "CUSTOM";
  props: {
    headline?: string;
    body?: string;
    buttonLabel?: string;
    buttonHref?: string;
    price?: string;
    testimonial?: string;
    question?: string;
    answer?: string;
    fields?: string[];
    background?: string;
    alignment?: "left" | "center";
    [key: string]: unknown;
  };
}

export interface SiteDocument {
  version: number;
  blocks: BuilderBlock[];
}

export interface OrganizationSummary {
  id: string;
  name: string;
  slug: string;
  role: Role;
}

export interface SiteSummary {
  id: string;
  organizationId: string;
  name: string;
  slug: string;
  status: SiteStatus;
  pageCount: number;
  updatedAt: string;
  publishedAt: string | null;
}

export interface PageSummary {
  id: string;
  siteId: string;
  title: string;
  slug: string;
  version: number;
  document: SiteDocument;
  updatedAt: string;
}

export interface BlockTemplateSummary {
  id: string;
  organizationId: string;
  name: string;
  category: BlockCategory;
  document: SiteDocument;
}

export interface ExperimentSummary {
  id: string;
  organizationId: string;
  siteId: string;
  name: string;
  status: ExperimentStatus;
  variants: Array<{
    id: string;
    name: string;
    trafficShare: number;
    pageDocument: SiteDocument;
  }>;
}

export interface AnalyticsSummary {
  totalEvents: number;
  byEventName: Record<string, number>;
  bySource: Record<string, number>;
  conversionRate: number;
}

export interface LayoutSuggestionSummary {
  id: string;
  siteId: string;
  title: string;
  rationale: string;
  confidence: number;
  proposedDocument: SiteDocument;
  createdAt: string;
}
