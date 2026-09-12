import { z } from "zod";

const slug = z
  .string()
  .min(2)
  .max(120)
  .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, "Use a URL-safe slug");

const builderBlock = z.object({
  id: z.string().min(1),
  type: z.enum(["HERO", "CTA", "PRICING", "TESTIMONIALS", "FORM", "FAQ", "CUSTOM"]),
  props: z.record(z.unknown()).default({})
});

export const siteDocumentSchema = z.object({
  version: z.number().int().positive().default(1),
  blocks: z.array(builderBlock).default([])
});

export const createOrganizationSchema = z.object({
  name: z.string().min(2).max(120),
  slug
});

export const updateOrganizationSchema = createOrganizationSchema.partial();

export const createSiteSchema = z.object({
  organizationId: z.string().min(1),
  name: z.string().min(2).max(120),
  slug
});

export const updateSiteSchema = z.object({
  name: z.string().min(2).max(120).optional(),
  slug: slug.optional(),
  status: z.enum(["DRAFT", "PUBLISHED", "ARCHIVED"]).optional()
});

export const createPageSchema = z.object({
  title: z.string().min(2).max(140),
  slug,
  document: siteDocumentSchema.optional()
});

export const updatePageSchema = z.object({
  title: z.string().min(2).max(140).optional(),
  slug: slug.optional(),
  document: siteDocumentSchema.optional()
});

export const createTemplateSchema = z.object({
  organizationId: z.string().min(1),
  name: z.string().min(2).max(140),
  category: z.enum(["HERO", "CTA", "PRICING", "TESTIMONIALS", "FORM", "FAQ"]),
  document: siteDocumentSchema
});

export const updateTemplateSchema = createTemplateSchema
  .omit({ organizationId: true })
  .partial();

export const createExperimentSchema = z.object({
  organizationId: z.string().min(1),
  siteId: z.string().min(1),
  name: z.string().min(2).max(140),
  variants: z
    .array(
      z.object({
        name: z.string().min(1).max(80),
        trafficShare: z.number().int().min(0).max(100),
        pageDocument: siteDocumentSchema
      })
    )
    .min(2)
});

export const updateExperimentSchema = z.object({
  name: z.string().min(2).max(140).optional(),
  status: z.enum(["DRAFT", "RUNNING", "PAUSED", "COMPLETED"]).optional(),
  variants: createExperimentSchema.shape.variants.optional()
});

export const conversionEventSchema = z.object({
  organizationId: z.string().min(1),
  siteId: z.string().min(1),
  pageSlug: slug,
  eventName: z.string().min(2).max(80),
  source: z.string().max(80).optional(),
  metadata: z.record(z.unknown()).optional()
});

export const figmaImportSchema = z.object({
  organizationId: z.string().min(1),
  siteId: z.string().min(1).optional(),
  name: z.string().min(2).max(120),
  nodes: z
    .array(
      z.object({
        id: z.string(),
        name: z.string(),
        type: z.string()
      })
    )
    .min(1)
});

export type CreateOrganizationInput = z.infer<typeof createOrganizationSchema>;
export type UpdateOrganizationInput = z.infer<typeof updateOrganizationSchema>;
export type CreateSiteInput = z.infer<typeof createSiteSchema>;
export type UpdateSiteInput = z.infer<typeof updateSiteSchema>;
export type CreatePageInput = z.infer<typeof createPageSchema>;
export type UpdatePageInput = z.infer<typeof updatePageSchema>;
export type CreateTemplateInput = z.infer<typeof createTemplateSchema>;
export type UpdateTemplateInput = z.infer<typeof updateTemplateSchema>;
export type CreateExperimentInput = z.infer<typeof createExperimentSchema>;
export type UpdateExperimentInput = z.infer<typeof updateExperimentSchema>;
export type ConversionEventInput = z.infer<typeof conversionEventSchema>;
export type FigmaImportInput = z.infer<typeof figmaImportSchema>;
