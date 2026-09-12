import { z } from 'zod';

export const tokenCategorySchema = z.enum([
  'color',
  'typography',
  'spacing',
  'radius',
  'shadow',
  'motion',
  'zIndex'
]);

export const tokenStatusSchema = z.enum(['draft', 'approved', 'rejected', 'deprecated']);

export const createTokenSetSchema = z
  .object({
    name: z.string().min(2).max(80),
    description: z.string().max(1000).optional(),
    source: z.string().max(120).optional()
  })
  .strict();

export const updateTokenSetSchema = createTokenSetSchema
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required'
  });

export const createTokenSchema = z
  .object({
    tokenSetId: z.string().uuid(),
    name: z.string().min(3).max(160),
    category: tokenCategorySchema,
    value: z.string().min(1).max(2000),
    description: z.string().max(1000).nullable().optional(),
    figmaNodeId: z.string().max(160).nullable().optional(),
    createdBy: z.string().max(160).optional(),
    changeNote: z.string().max(1000).optional()
  })
  .strict();

export const updateTokenSchema = createTokenSchema
  .omit({ tokenSetId: true })
  .extend({
    status: tokenStatusSchema.optional(),
    deprecated: z.boolean().optional()
  })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required'
  });

export const actorActionSchema = z
  .object({
    actor: z.string().max(160).optional(),
    changeNote: z.string().max(1000).optional()
  })
  .strict();
