/**
 * Typed API client for the ShopFlow gateway.
 *
 * Unwraps the shared `ApiResponse<T>` envelope and throws a typed `ApiError` on
 * failure. Attaches the bearer token (when present) for authenticated calls.
 */
import type {
  ApiResponse,
  Order,
  Page,
  Product,
  ProductNode,
  Review,
  SearchResult,
  TokenPair,
} from '@/types/catalog';

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const TOKEN_KEY = 'shopflow.accessToken';

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
    public readonly traceId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (typeof window === 'undefined') return;
  if (token) window.localStorage.setItem(TOKEN_KEY, token);
  else window.localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string>),
  };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, { ...init, headers, cache: 'no-store' });
  } catch {
    throw new ApiError('NETWORK_ERROR', 'Unable to reach the server', 0);
  }

  // 204 / empty body
  const text = await res.text();
  const body = (text ? JSON.parse(text) : null) as ApiResponse<T> | null;

  if (!res.ok || (body && !body.success)) {
    throw new ApiError(
      body?.error?.code ?? 'HTTP_ERROR',
      body?.error?.message ?? res.statusText,
      res.status,
      body?.traceId,
    );
  }
  return (body?.data ?? null) as T;
}

export const catalogApi = {
  listProducts(params?: { category?: string; page?: number; size?: number }): Promise<Page<Product>> {
    const q = new URLSearchParams();
    if (params?.category) q.set('category', params.category);
    if (params?.page != null) q.set('page', String(params.page));
    if (params?.size != null) q.set('size', String(params.size));
    const qs = q.toString();
    return request<Page<Product>>(`/products${qs ? `?${qs}` : ''}`);
  },
  getProduct(id: string): Promise<Product> {
    return request<Product>(`/products/${encodeURIComponent(id)}`);
  },
  categories(): Promise<string[]> {
    return request<string[]>('/products/categories');
  },
  listReviews(productId: string): Promise<Page<Review>> {
    return request<Page<Review>>(`/products/${encodeURIComponent(productId)}/reviews`);
  },
  addReview(productId: string, body: { author: string; rating: number; text: string }): Promise<Review> {
    return request<Review>(`/products/${encodeURIComponent(productId)}/reviews`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },
};

export const searchApi = {
  search(params: { q?: string; category?: string; page?: number; size?: number }): Promise<SearchResult<Product>> {
    const qp = new URLSearchParams();
    if (params.q) qp.set('q', params.q);
    if (params.category) qp.set('category', params.category);
    if (params.page != null) qp.set('page', String(params.page));
    if (params.size != null) qp.set('size', String(params.size));
    return request<SearchResult<Product>>(`/search?${qp.toString()}`);
  },
};

export const recommendationApi = {
  alsoBought(productId: string, limit = 6): Promise<ProductNode[]> {
    return request<ProductNode[]>(
      `/recommendations/products/${encodeURIComponent(productId)}/also-bought?limit=${limit}`,
    );
  },
  trending(limit = 6): Promise<ProductNode[]> {
    return request<ProductNode[]>(`/recommendations/trending?limit=${limit}`);
  },
};

export const orderApi = {
  create(body: {
    customerId: string;
    currency: string;
    items: { productId: string; quantity: number; unitPrice: number }[];
  }, idempotencyKey?: string): Promise<Order> {
    return request<Order>('/orders', {
      method: 'POST',
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
      body: JSON.stringify(body),
    });
  },
  get(id: string): Promise<Order> {
    return request<Order>(`/orders/${encodeURIComponent(id)}`);
  },
  listForCustomer(customerId: string): Promise<Page<Order>> {
    return request<Page<Order>>(`/orders?customerId=${encodeURIComponent(customerId)}`);
  },
  pay(id: string): Promise<Order> {
    return request<Order>(`/orders/${encodeURIComponent(id)}/payment`, { method: 'POST' });
  },
};

export const authApi = {
  login(username: string, password: string): Promise<TokenPair> {
    return request<TokenPair>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
  },
  register(username: string, email: string, password: string): Promise<{ username: string }> {
    return request<{ username: string }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, email, password }),
    });
  },
};
