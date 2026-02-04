import { apiClient } from './apiClient';

// Types
export interface Widget {
  id: string;
  title: string;
  description?: string;
  type: string;
  config: Record<string, any>;
  position: { x: number; y: number; width: number; height: number };
  dataSource?: string;
  query?: string;
  refreshRate?: number;
  dashboardId: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
  cachedData?: any;
  lastFetch?: string;
  dashboard?: {
    id: string;
    title: string;
    isPublic?: boolean;
  };
  user?: {
    id: string;
    firstName?: string;
    lastName?: string;
    email?: string;
  };
}

export interface WidgetTemplate {
  id: string;
  title: string;
  description?: string;
  type: string;
  config: Record<string, any>;
  author: string;
  dashboard?: string;
  createdAt: string;
  downloads: number;
}

export interface SharedWidget extends Widget {
  sharedBy: string;
  permission: 'READ' | 'WRITE' | 'ADMIN';
}

export interface CreateWidgetData {
  title: string;
  description?: string;
  type: string;
  config?: Record<string, any>;
  position?: { x: number; y: number; width: number; height: number };
  dashboardId: string;
  dataSource?: string;
  query?: string;
  refreshRate?: number;
}

export interface UpdateWidgetData {
  title?: string;
  description?: string;
  type?: string;
  config?: Record<string, any>;
  position?: { x: number; y: number; width: number; height: number };
  dataSource?: string;
  query?: string;
  refreshRate?: number;
}

export interface PaginatedResponse<T> {
  success: boolean;
  data: T[];
  meta: {
    total: number;
    page: number;
    limit: number;
    pages: number;
    hasNext?: boolean;
    hasPrev?: boolean;
  };
  message: string;
}

export interface WidgetFilters {
  page?: number;
  limit?: number;
  search?: string;
  type?: string;
  sortBy?: 'createdAt' | 'updatedAt' | 'title';
  sortOrder?: 'asc' | 'desc';
}

// API functions
export const widgetApi = {
  // Get user's widgets
  getMyWidgets: async (filters: WidgetFilters = {}): Promise<PaginatedResponse<Widget>> => {
    const params = new URLSearchParams();
    if (filters.page) params.append('page', String(filters.page));
    if (filters.limit) params.append('limit', String(filters.limit));
    if (filters.search) params.append('search', filters.search);
    if (filters.type) params.append('type', filters.type);
    if (filters.sortBy) params.append('sortBy', filters.sortBy);
    if (filters.sortOrder) params.append('sortOrder', filters.sortOrder);

    const response = await apiClient.get(`/widgets?${params.toString()}`);
    return response.data;
  },

  // Get shared widgets
  getSharedWidgets: async (filters: WidgetFilters = {}): Promise<PaginatedResponse<SharedWidget>> => {
    const params = new URLSearchParams();
    if (filters.page) params.append('page', String(filters.page));
    if (filters.limit) params.append('limit', String(filters.limit));
    if (filters.search) params.append('search', filters.search);
    if (filters.type) params.append('type', filters.type);

    const response = await apiClient.get(`/widgets/shared?${params.toString()}`);
    return response.data;
  },

  // Get widget templates
  getTemplates: async (filters: WidgetFilters = {}): Promise<PaginatedResponse<WidgetTemplate>> => {
    const params = new URLSearchParams();
    if (filters.page) params.append('page', String(filters.page));
    if (filters.limit) params.append('limit', String(filters.limit));
    if (filters.search) params.append('search', filters.search);
    if (filters.type) params.append('type', filters.type);

    const response = await apiClient.get(`/widgets/templates?${params.toString()}`);
    return response.data;
  },

  // Get single widget
  getWidget: async (id: string): Promise<{ success: boolean; data: Widget }> => {
    const response = await apiClient.get(`/widgets/${id}`);
    return response.data;
  },

  // Create widget
  createWidget: async (data: CreateWidgetData): Promise<{ success: boolean; data: Widget }> => {
    const response = await apiClient.post('/widgets', data);
    return response.data;
  },

  // Update widget
  updateWidget: async (id: string, data: UpdateWidgetData): Promise<{ success: boolean; data: Widget }> => {
    const response = await apiClient.put(`/widgets/${id}`, data);
    return response.data;
  },

  // Delete widget
  deleteWidget: async (id: string): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.delete(`/widgets/${id}`);
    return response.data;
  },

  // Duplicate widget
  duplicateWidget: async (id: string, targetDashboardId?: string): Promise<{ success: boolean; data: Widget }> => {
    const response = await apiClient.post(`/widgets/${id}/duplicate`, { targetDashboardId });
    return response.data;
  },

  // Refresh widget data
  refreshWidget: async (id: string): Promise<{ success: boolean; data: { lastFetch: string } }> => {
    const response = await apiClient.post(`/widgets/${id}/refresh`);
    return response.data;
  },
};

export default widgetApi;
