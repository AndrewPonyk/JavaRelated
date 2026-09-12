export interface User {
  id: string;
  email: string;
  created_at: string;
  updated_at: string;
}

export interface SyncStatus {
  lastSyncAt: string | null;
  status: 'pending' | 'success' | 'failed';
  totalRecords: number;
  deletedRecords: number;
}

export interface SyncItem {
  id: string;
  collection_name: string;
  record_id: string;
  payload: Record<string, unknown>;
  client_updated_at: string;
  last_synced_at: string;
  deleted_at: string | null;
  'deleted?': boolean;
}

interface AuthResponse {
  token: string;
  expires_at: string;
  user: User;
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:3000';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('jwt_token');
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {})
    }
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = data?.error?.message ?? 'Request failed';
    throw new Error(Array.isArray(message) ? message.join(', ') : message);
  }

  return data as T;
}

export const api = {
  async register(email: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ user: { email, password, password_confirmation: password } })
    });
  },

  async login(email: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ user: { email, password } })
    });
  },

  async logout(): Promise<void> {
    await request('/api/v1/auth/logout', { method: 'DELETE' });
    localStorage.removeItem('jwt_token');
  },

  async me(): Promise<{ user: User }> {
    return request<{ user: User }>('/api/v1/auth/me');
  },

  async syncStatus(): Promise<SyncStatus> {
    return request<SyncStatus>('/api/v1/sync/status');
  },

  async syncItems(): Promise<{ sync_items: SyncItem[] }> {
    return request<{ sync_items: SyncItem[] }>('/api/v1/sync_items');
  },

  async createNote(title: string): Promise<{ sync_item: SyncItem }> {
    return request<{ sync_item: SyncItem }>('/api/v1/sync_items', {
      method: 'POST',
      body: JSON.stringify({
        sync_item: {
          collection_name: 'notes',
          record_id: crypto.randomUUID(),
          payload: { title },
          client_updated_at: new Date().toISOString()
        }
      })
    });
  }
};
