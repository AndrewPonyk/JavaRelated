// Typed API surface — one function per backend endpoint. Components and hooks
// call these rather than touching `apiFetch` directly.
import { apiFetch } from "./client";
import type {
  Cart,
  LoginResponse,
  Order,
  Paginated,
  Product,
  User,
  Vendor,
} from "@/types";

export const authApi = {
  register: (body: { email: string; password: string; role?: string }) =>
    apiFetch<User>("/auth/register/", { method: "POST", body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) =>
    apiFetch<LoginResponse>("/auth/token/", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  me: () => apiFetch<User>("/auth/me/"),
  logout: (refresh: string) =>
    apiFetch<void>("/auth/logout/", {
      method: "POST",
      body: JSON.stringify({ refresh }),
    }),
};

export const catalogApi = {
  list: (params: Record<string, string> = {}) => {
    const qs = new URLSearchParams(params).toString();
    return apiFetch<Paginated<Product>>(`/catalog/products/${qs ? `?${qs}` : ""}`);
  },
  retrieve: (slug: string) => apiFetch<Product>(`/catalog/products/${slug}/`),
  create: (body: Record<string, unknown>) =>
    apiFetch<Product>("/catalog/products/", {
      method: "POST",
      body: JSON.stringify(body),
    }),
};

export const cartApi = {
  get: () => apiFetch<Cart>("/cart/"),
  addItem: (product_id: number, quantity: number) =>
    apiFetch<Cart>("/cart/items/", {
      method: "POST",
      body: JSON.stringify({ product_id, quantity }),
    }),
  updateItem: (id: number, quantity: number) =>
    apiFetch<Cart>(`/cart/items/${id}/`, {
      method: "PATCH",
      body: JSON.stringify({ quantity }),
    }),
  removeItem: (id: number) =>
    apiFetch<Cart>(`/cart/items/${id}/`, { method: "DELETE" }),
  clear: () => apiFetch<Cart>("/cart/", { method: "DELETE" }),
};

export const ordersApi = {
  list: () => apiFetch<Paginated<Order>>("/orders/"),
  checkout: (payment_token: string) =>
    apiFetch<Order>("/orders/checkout/", {
      method: "POST",
      body: JSON.stringify({ payment_token }),
    }),
  cancel: (number: string) =>
    apiFetch<Order>(`/orders/${number}/cancel/`, { method: "POST" }),
};

export interface SearchResponse {
  results: Array<Record<string, unknown>>;
  total: number;
  facets: Record<string, unknown>;
}

export const searchApi = {
  search: (params: Record<string, string>) => {
    const qs = new URLSearchParams(params).toString();
    return apiFetch<SearchResponse>(`/search/?${qs}`);
  },
  autocomplete: (q: string) =>
    apiFetch<{ suggestions: string[] }>(
      `/search/autocomplete/?q=${encodeURIComponent(q)}`,
    ),
};

export const recommendationsApi = {
  forMe: () =>
    apiFetch<{ source: string; results: Product[] }>("/recommendations/"),
  track: (product_id: number, event: "view" | "add_to_cart") =>
    apiFetch<void>("/recommendations/track/", {
      method: "POST",
      body: JSON.stringify({ product_id, event }),
    }),
};

export const vendorsApi = {
  me: () => apiFetch<Vendor>("/vendors/me/"),
  create: (name: string) =>
    apiFetch<Vendor>("/vendors/", { method: "POST", body: JSON.stringify({ name }) }),
};
