// Shared domain types. Ideally generated from the backend OpenAPI schema to
// prevent contract drift (see TECH-NOTES §3.6).

export type Role = "customer" | "vendor" | "staff";

export interface User {
  id: number;
  email: string;
  username: string;
  role: Role;
  first_name: string;
  last_name: string;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  parent: number | null;
}

export interface VendorBrief {
  id: number;
  name: string;
  slug: string;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  slug: string;
  description: string;
  price: string; // Decimal serialized as string to avoid float rounding.
  currency: string;
  status: "draft" | "active" | "archived";
  category: Category;
  vendor: VendorBrief | null;
  is_purchasable: boolean;
  quantity_available: number;
  created_at: string;
}

export interface CartItem {
  id: number;
  product: number;
  product_name: string;
  quantity: number;
  unit_price: string;
  line_total: string;
}

export interface Cart {
  id: number;
  items: CartItem[];
  total: string;
  item_count: number;
  updated_at: string;
}

export interface OrderItem {
  product: number;
  product_name: string;
  quantity: number;
  unit_price: string;
}

export interface Order {
  id: number;
  number: string;
  status: "pending" | "paid" | "fulfilled" | "cancelled" | "refunded";
  total_amount: string;
  currency: string;
  items: OrderItem[];
  item_count: number;
  created_at: string;
}

export interface Vendor {
  id: number;
  name: string;
  slug: string;
  status: "pending" | "approved" | "suspended";
  created_at: string;
}

export interface Paginated<T> {
  count: number;
  next: string | null;
  previous: string | null;
  results: T[];
}

export interface AuthTokens {
  access: string;
  refresh: string;
}

export interface LoginResponse extends AuthTokens {
  user: User;
}

export interface ApiError {
  error: {
    code: string;
    message: string;
    details: Record<string, unknown>;
    request_id: string | null;
  };
}
