/** Entity-linking / risk-stratification API calls. */
import { apiPost } from '@/api/client';
import type { StratificationRequest, StratificationResponse } from '@/types/api';

export async function stratify(request: StratificationRequest): Promise<StratificationResponse> {
  return apiPost<StratificationResponse>('/stratification', request);
}
