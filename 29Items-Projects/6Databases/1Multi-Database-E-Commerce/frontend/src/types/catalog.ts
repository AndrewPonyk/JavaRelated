/** Domain types mirroring the backend services and the shared API envelope. */

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string;
  category: string;
  price: number;
  currency: string;
  active: boolean;
  stockOnHand: number;
  averageRating: number;
  reviewCount: number;
}

export interface Review {
  id: string;
  productId: string;
  author: string;
  rating: number;
  text: string;
  sentimentLabel: string | null;
  sentimentScore: number | null;
  createdAt: string;
}

export interface OrderLine {
  productId: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  id: string;
  customerId: string;
  status: 'PENDING' | 'PAID' | 'FULFILLED' | 'CANCELLED';
  currency: string;
  totalAmount: number;
  createdAt: string;
  items: OrderLine[];
}

export interface ProductNode {
  id: string;
  name: string;
  category: string | null;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

/** Spring Data `Page<T>` shape returned by paginated endpoints. */
export interface Page<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** search-service returns its own lightweight result shape. */
export interface SearchResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

/** The uniform envelope every ShopFlow service returns (see common/ApiResponse). */
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
    fieldErrors?: { field: string; message: string }[];
  };
  timestamp: string;
  traceId?: string;
}
