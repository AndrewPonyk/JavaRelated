import { apiGet, apiPatch, apiPost } from './client';
import type { CatalogProduct, ProductSearchResult } from '@/types/product';

/** Calls the public product search (Elasticsearch-backed read model). */
export function searchProducts(
  term: string,
  page = 1,
  signal?: AbortSignal,
): Promise<ProductSearchResult> {
  const params = new URLSearchParams({ q: term, page: String(page) });
  return apiGet<ProductSearchResult>(`/search?${params.toString()}`, signal);
}

export interface CreateProductInput {
  name: string;
  description: string;
  priceMinor: number;
  currency: string;
  stock: number;
}

/** Create a product (seller only). Returns the new product id. */
export function createProduct(input: CreateProductInput): Promise<{ id: string }> {
  return apiPost<{ id: string }>('/products', input);
}

/** List the authenticated seller's own products. */
export function listMyProducts(): Promise<{ items: CatalogProduct[] }> {
  return apiGet<{ items: CatalogProduct[] }>('/products/mine');
}

/** Update a product's price (seller only; ownership enforced server-side). */
export function updateProductPrice(id: string, priceMinor: number): Promise<CatalogProduct> {
  return apiPatch<CatalogProduct>(`/products/${id}`, { priceMinor });
}
