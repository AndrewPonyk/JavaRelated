/**
 * API contract types — mirror backend/app/schemas/* exactly (snake_case on the wire).
 * Keep in sync when the backend contract changes.
 */

export type SortOption = "relevance" | "price_asc" | "price_desc" | "newest";

export interface SearchParams {
  q: string;
  category?: string[];
  brand?: string[];
  price_min?: number;
  price_max?: number;
  sort?: SortOption;
  page?: number;
  size?: number;
}

export interface ProductHit {
  id: string;
  sku: string;
  name: string;
  brand: string | null;
  price: number;
  category_slug: string | null;
  in_stock: boolean;
  score: number | null;
}

export interface FacetValue {
  value: string;
  count: number;
  selected: boolean;
}

export interface FacetGroup {
  name: string;
  label: string;
  values: FacetValue[];
}

export interface PageMeta {
  page: number;
  size: number;
  total: number;
  pages: number;
}

export interface SearchResponse {
  query: string;
  hits: ProductHit[];
  facets: FacetGroup[];
  meta: PageMeta;
  took_ms: number;
}

export interface SuggestResponse {
  query: string;
  suggestions: string[];
}
