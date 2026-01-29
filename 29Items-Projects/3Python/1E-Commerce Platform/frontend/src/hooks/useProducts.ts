/**
 * Products hooks with React Query
 */
import { useQuery, useInfiniteQuery } from '@tanstack/react-query';
import { productService, ProductFilters } from '../services/products';

export function useProducts(filters: ProductFilters = {}) {
  return useQuery({
    queryKey: ['products', filters],
    queryFn: () => productService.getProducts(filters),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

export function useProduct(slug: string) {
  return useQuery({
    queryKey: ['product', slug],
    queryFn: () => productService.getProduct(slug),
    enabled: !!slug,
    staleTime: 5 * 60 * 1000,
  });
}

export function useFeaturedProducts(limit = 12) {
  return useQuery({
    queryKey: ['products', 'featured', limit],
    queryFn: () => productService.getFeaturedProducts(limit),
    staleTime: 5 * 60 * 1000,
  });
}

export function useSearchProducts(query: string, filters: ProductFilters = {}) {
  return useQuery({
    queryKey: ['search', query, filters],
    queryFn: () => productService.searchProducts(query, filters),
    enabled: query.length >= 2,
    staleTime: 60 * 1000, // 1 minute for search results
  });
}

export function useInfiniteProducts(filters: ProductFilters = {}) {
  return useInfiniteQuery({
    queryKey: ['products', 'infinite', filters],
    queryFn: ({ pageParam = 1 }) =>
      productService.getProducts({ ...filters, page: pageParam }),
    getNextPageParam: (lastPage, pages) => {
      if (!lastPage.next) return undefined;
      return pages.length + 1;
    },
    initialPageParam: 1,
    staleTime: 5 * 60 * 1000,
  });
}

export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: productService.getCategories,
    staleTime: 30 * 60 * 1000, // 30 minutes for categories
  });
}

export function useCategory(slug: string) {
  return useQuery({
    queryKey: ['category', slug],
    queryFn: () => productService.getCategory(slug),
    enabled: !!slug,
    staleTime: 30 * 60 * 1000,
  });
}
