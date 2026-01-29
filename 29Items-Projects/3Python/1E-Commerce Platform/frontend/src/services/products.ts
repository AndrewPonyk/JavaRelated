/**
 * Product API service
 */
import api from './api';
import type {
  Category,
  PaginatedResponse,
  Product,
  ProductListItem,
  SearchResult,
} from '../types';

export interface ProductFilters {
  category?: string;
  vendor?: string;
  min_price?: number;
  max_price?: number;
  sort?: string;
  search?: string;
  page?: number;
  page_size?: number;
}

export const productService = {
  /**
   * Get paginated list of products
   */
  async getProducts(filters: ProductFilters = {}): Promise<PaginatedResponse<ProductListItem>> {
    const params = new URLSearchParams();

    if (filters.category) params.append('category__slug', filters.category);
    if (filters.search) params.append('search', filters.search);
    if (filters.sort) params.append('ordering', filters.sort);
    if (filters.page) params.append('page', String(filters.page));
    if (filters.page_size) params.append('page_size', String(filters.page_size));

    const response = await api.get<PaginatedResponse<ProductListItem>>(
      `/products/?${params.toString()}`
    );
    return response.data;
  },

  /**
   * Get product details by slug
   */
  async getProduct(slug: string): Promise<Product> {
    const response = await api.get<Product>(`/products/${slug}/`);
    return response.data;
  },

  /**
   * Get featured products
   */
  async getFeaturedProducts(limit = 12): Promise<ProductListItem[]> {
    const response = await api.get<PaginatedResponse<ProductListItem> | ProductListItem[]>('/products/featured/', {
      params: { limit },
    });
    // Handle both paginated and non-paginated responses
    if ('results' in response.data) {
      return response.data.results;
    }
    alert("test");
    return response.data;
  },

  /**
   * Get products by category
   */
  async getProductsByCategory(
    categorySlug: string,
    page = 1
  ): Promise<PaginatedResponse<ProductListItem>> {
    const response = await api.get<PaginatedResponse<ProductListItem>>(
      `/products/by_category/`,
      { params: { category: categorySlug, page } }
    );
    return response.data;
  },

  /**
   * Search products with facets
   */
  async searchProducts(
    query: string,
    filters: Omit<ProductFilters, 'search'> = {}
  ): Promise<SearchResult> {
    const params = new URLSearchParams();
    params.append('q', query);

    if (filters.category) params.append('category', filters.category);
    if (filters.vendor) params.append('vendor', filters.vendor);
    if (filters.min_price) params.append('min_price', String(filters.min_price));
    if (filters.max_price) params.append('max_price', String(filters.max_price));
    if (filters.sort) params.append('sort', filters.sort);
    if (filters.page) params.append('page', String(filters.page));
    if (filters.page_size) params.append('page_size', String(filters.page_size));

    const response = await api.get<SearchResult>(
      `/search/products/?${params.toString()}`
    );
    return response.data;
  },

  /**
   * Get search suggestions
   */
  async getSearchSuggestions(query: string): Promise<{ suggestions: { text: string; slug: string }[] }> {
    const response = await api.get(`/search/suggest/`, {
      params: { q: query },
    });
    return response.data;
  },

  /**
   * Get all categories
   */
  async getCategories(): Promise<Category[]> {
    const response = await api.get<PaginatedResponse<Category> | Category[]>('/products/categories/');
    // Handle both paginated and non-paginated responses
    if ('results' in response.data) {
      return response.data.results;
    }
    return response.data;
  },

  /**
   * Get category by slug
   */
  async getCategory(slug: string): Promise<Category> {
    const response = await api.get<Category>(`/products/categories/${slug}/`);
    return response.data;
  },

  /**
   * Add product review
   */
  async addReview(
    productSlug: string,
    review: { rating: number; title: string; content: string }
  ): Promise<void> {
    await api.post(`/products/${productSlug}/add_review/`, review);
  },
};
