import axios from 'axios';

import type { DrugInput, DrugSearchResult, InteractionCheckResponse } from '../types';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 15000,
});

export async function checkInteractions(
  drugs: DrugInput[],
  includeMlPrediction = true,
): Promise<InteractionCheckResponse> {
  const { data } = await api.post<InteractionCheckResponse>('/interactions/check', {
    drugs,
    include_ml_prediction: includeMlPrediction,
  });
  return data;
}

export async function searchDrugs(query: string): Promise<DrugSearchResult> {
  const { data } = await api.get<DrugSearchResult>('/drugs/search', { params: { q: query } });
  return data;
}

export default api;
