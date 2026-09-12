import { z } from 'zod';

/**
 * Zod schemas mirror the backend DTOs (InvoiceDto / OfferDto). We parse responses against
 * these at the API boundary because the wire is untyped — a server change that drops a
 * field fails loudly here instead of causing `undefined` bugs deep in the UI.
 */
export const offerSchema = z.object({
  riskGrade: z.string(),
  probabilityOfDefault: z.number(),
  advanceRate: z.number(),
  discountFeeRate: z.number(),
  netDisbursement: z.number(),
  fee: z.number(),
});

export const invoiceSchema = z.object({
  id: z.string().uuid(),
  companyId: z.string().uuid(),
  debtorName: z.string(),
  faceValue: z.number(),
  currency: z.string(),
  issueDate: z.string(),
  dueDate: z.string(),
  status: z.string(),
  offer: offerSchema.nullable(),
  declineReason: z.string().nullable(),
  createdAt: z.string(),
});

export type Offer = z.infer<typeof offerSchema>;
export type Invoice = z.infer<typeof invoiceSchema>;

export const INVOICE_STATUSES = [
  'Submitted',
  'UnderReview',
  'Approved',
  'Declined',
  'Disbursing',
  'Outstanding',
  'Repaid',
  'Defaulted',
] as const;

export type InvoiceStatus = (typeof INVOICE_STATUSES)[number];
