import { z } from 'zod';

export const filterNames = [
  'grayscale',
  'invert',
  'sepia',
  'brightness',
  'contrast',
  'saturation',
  'blur',
  'sharpen',
] as const;

export type FilterName = (typeof filterNames)[number];

export const filterSchema = z
  .object({
    name: z.enum(filterNames),
    amount: z.number().finite().min(-100).max(100).default(0),
  })
  .strict()
  .superRefine((filter, context) => {
    if (['grayscale', 'invert', 'sepia'].includes(filter.name) && filter.amount !== 0) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'This filter does not accept an amount.',
      });
    }
    if (filter.name === 'blur' && (filter.amount < 1 || filter.amount > 8)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Blur amount must be from 1 to 8.',
      });
    }
    if (filter.name === 'sharpen' && (filter.amount < 1 || filter.amount > 100)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Sharpen amount must be from 1 to 100.',
      });
    }
  });

export const normalizedCropSchema = z
  .object({
    x: z.number().finite().min(0).max(1),
    y: z.number().finite().min(0).max(1),
    width: z.number().finite().positive().max(1),
    height: z.number().finite().positive().max(1),
  })
  .strict()
  .refine((crop) => crop.x + crop.width <= 1 && crop.y + crop.height <= 1, {
    message: 'Crop rectangle must fit within normalized image bounds.',
  });

export const editRecipeSchema = z
  .object({
    schemaVersion: z.literal(1),
    filters: z.array(filterSchema).max(20),
    crop: normalizedCropSchema.optional(),
  })
  .strict();

export const projectCreateSchema = z.object({ name: z.string().trim().min(1).max(120) }).strict();
export const projectUpdateSchema = projectCreateSchema;

export const presetCreateSchema = z
  .object({
    name: z.string().trim().min(1).max(80),
    projectId: z.string().uuid().nullable().optional(),
    recipe: editRecipeSchema,
  })
  .strict();
export const presetUpdateSchema = presetCreateSchema
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field must be supplied.',
  });

export const anonymousSessionSchema = z
  .object({ displayName: z.string().trim().min(1).max(60).optional() })
  .strict();

export const paginationSchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(50),
  offset: z.coerce.number().int().min(0).max(10_000).default(0),
});

export type FilterRecipe = z.infer<typeof filterSchema>;
export type NormalizedCrop = z.infer<typeof normalizedCropSchema>;
export type EditRecipe = z.infer<typeof editRecipeSchema>;
export type ProjectCreate = z.infer<typeof projectCreateSchema>;
export type ProjectUpdate = z.infer<typeof projectUpdateSchema>;
export type PresetCreate = z.infer<typeof presetCreateSchema>;
export type PresetUpdate = z.infer<typeof presetUpdateSchema>;
export type Pagination = z.infer<typeof paginationSchema>;
