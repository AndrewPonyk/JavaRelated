import axios from 'axios';
import { z } from 'zod';
import { config } from '@/lib/config';
import { invoiceSchema, type Invoice } from '@/types/invoice';

const http = axios.create({
  baseURL: config.apiBaseUrl,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

// Attach the bearer token to every request.
http.interceptors.request.use((request) => {
  // TODO: pull the access token from the auth provider (e.g. MSAL acquireTokenSilent).
  const token: string | null = null;
  if (token) request.headers.Authorization = `Bearer ${token}`;
  return request;
});

export interface SubmitInvoicePayload {
  companyId: string;
  debtorName: string;
  debtorTaxId: string;
  amount: number;
  currency: string;
  issueDate: string;
  dueDate: string;
  documentUri?: string | null;
}

const submitResponseSchema = z.object({ invoiceId: z.string().uuid(), status: z.string() });
const advanceSchema = z.object({
  id: z.string().uuid(),
  invoiceId: z.string().uuid(),
  netDisbursed: z.number(),
  fee: z.number(),
  reserve: z.number(),
  currency: z.string(),
  status: z.string(),
  stripePayoutId: z.string().nullable(),
});

export const invoicesApi = {
  async list(companyId: string): Promise<Invoice[]> {
    const { data } = await http.get('/api/invoices', { params: { companyId } });
    return z.array(invoiceSchema).parse(data);
  },

  async getById(id: string): Promise<Invoice> {
    const { data } = await http.get(`/api/invoices/${id}`);
    return invoiceSchema.parse(data);
  },

  async submit(payload: SubmitInvoicePayload) {
    const { data } = await http.post('/api/invoices', payload);
    return submitResponseSchema.parse(data);
  },

  async acceptOffer(invoiceId: string, idempotencyKey: string) {
    const { data } = await http.post(`/api/invoices/${invoiceId}/accept-offer`, null, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
    return advanceSchema.parse(data);
  },
};
