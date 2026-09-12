import { z } from 'zod';

// Tracks contracts/openapi.yaml → DashboardSummary / ActivityEntry.

export const balancePointSchema = z.object({
  month: z.string().regex(/^\d{4}-\d{2}$/),
  balance: z.number(),
});
export type BalancePoint = z.infer<typeof balancePointSchema>;

export const dashboardSummarySchema = z.object({
  balance: z.number(),
  currency: z.string().length(3),
  openTickets: z.number().int().nonnegative(),
  lastLoginAt: z.string().nullable(),
  balanceHistory: z.array(balancePointSchema),
});
export type DashboardSummary = z.infer<typeof dashboardSummarySchema>;

export const activityEntrySchema = z.object({
  id: z.string(),
  date: z.string(),
  description: z.string(),
  amount: z.number(),
  currency: z.string().length(3),
});
export type ActivityEntry = z.infer<typeof activityEntrySchema>;

export const activityListSchema = z.array(activityEntrySchema);
