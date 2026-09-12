import type { SearchParams, SearchResponse, SuggestResponse } from "@/types/search";

import { apiGet } from "./client";

export function searchProducts(params: SearchParams, signal?: AbortSignal): Promise<SearchResponse> {
  return apiGet<SearchResponse>("/search", { ...params }, signal);
}

export async function fetchSuggestions(q: string, signal?: AbortSignal): Promise<string[]> {
  const response = await apiGet<SuggestResponse>("/suggest", { q, limit: 8 }, signal);
  return response.suggestions;
}
