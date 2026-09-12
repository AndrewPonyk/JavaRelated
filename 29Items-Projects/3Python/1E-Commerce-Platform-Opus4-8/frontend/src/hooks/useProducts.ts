// Data-fetching hook built on TanStack Query — server state lives in the query
// cache, not in global UI state (see TECH-NOTES §3.6).
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/api/client";
import type { Paginated, Product } from "@/types";

export function useProducts(categorySlug?: string) {
  return useQuery({
    queryKey: ["products", categorySlug ?? "all"],
    queryFn: () => {
      const qs = categorySlug ? `?category=${encodeURIComponent(categorySlug)}` : "";
      return apiFetch<Paginated<Product>>(`/catalog/products/${qs}`);
    },
    staleTime: 60_000,
  });
}
