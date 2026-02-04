import { apiClient } from './apiClient';
import { Dashboard, Widget } from '@/types/dashboard';

// Types for API responses
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  meta?: {
    total: number;
    page: number;
    limit: number;
    pages: number;
  };
}

export interface DashboardFilters {
  page?: number;
  limit?: number;
  search?: string;
  isPublic?: boolean;
  isTemplate?: boolean;
  sortBy?: 'title' | 'createdAt' | 'updatedAt';
  sortOrder?: 'asc' | 'desc';
}

export interface ShareInfo {
  userId: string;
  email: string;
  name: string;
  permission: 'READ' | 'WRITE' | 'ADMIN';
  sharedAt: string;
}

export interface ExportJob {
  id: string;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  format: 'pdf' | 'png' | 'csv' | 'json';
  progress: number;
  downloadUrl?: string;
  error?: string;
  createdAt: string;
}

export interface DashboardVersion {
  id: string;
  dashboardId: string;
  version: number;
  title: string;
  description?: string;
  createdAt: string;
  createdBy: string;
  createdByName: string;
}

export interface DashboardComment {
  id: string;
  dashboardId: string;
  content: string;
  authorId: string;
  authorName: string;
  authorAvatar?: string;
  parentId?: string;
  createdAt: string;
  updatedAt: string;
  replies?: DashboardComment[];
}

export interface DashboardAnalytics {
  totalViews: number;
  uniqueViewers: number;
  viewsByDate: Array<{ date: string; views: number }>;
  topViewers: Array<{ userId: string; name: string; views: number }>;
  avgTimeOnDashboard: number;
}

export const dashboardApi = {
  // Get user's dashboards
  getDashboards: async (filters?: DashboardFilters): Promise<ApiResponse<Dashboard[]>> => {
    const params = new URLSearchParams();
    if (filters?.page) params.append('page', filters.page.toString());
    if (filters?.limit) params.append('limit', filters.limit.toString());
    if (filters?.search) params.append('search', filters.search);
    if (filters?.isPublic !== undefined) params.append('isPublic', filters.isPublic.toString());
    if (filters?.isTemplate !== undefined) params.append('isTemplate', filters.isTemplate.toString());
    if (filters?.sortBy) params.append('sortBy', filters.sortBy);
    if (filters?.sortOrder) params.append('sortOrder', filters.sortOrder);

    const response = await apiClient.get(`/dashboards?${params.toString()}`);
    return response.data;
  },

  // Get single dashboard by ID
  getDashboard: async (id: string): Promise<ApiResponse<Dashboard>> => {
    const response = await apiClient.get(`/dashboards/${id}`);
    return response.data;
  },

  // Create new dashboard
  createDashboard: async (dashboard: Partial<Dashboard>): Promise<ApiResponse<Dashboard>> => {
    const response = await apiClient.post('/dashboards', dashboard);
    return response.data;
  },

  // Update dashboard
  updateDashboard: async (id: string, dashboard: Partial<Dashboard>): Promise<ApiResponse<Dashboard>> => {
    const response = await apiClient.put(`/dashboards/${id}`, dashboard);
    return response.data;
  },

  // Delete dashboard
  deleteDashboard: async (id: string): Promise<ApiResponse<{ deleted: boolean }>> => {
    const response = await apiClient.delete(`/dashboards/${id}`);
    return response.data;
  },

  // Duplicate dashboard
  duplicateDashboard: async (id: string, title?: string): Promise<ApiResponse<Dashboard>> => {
    const response = await apiClient.post(`/dashboards/${id}/duplicate`, { title });
    return response.data;
  },

  // Share dashboard
  shareDashboard: async (
    id: string,
    shares: Array<{ userId: string; permission: 'READ' | 'WRITE' | 'ADMIN' }>
  ): Promise<ApiResponse<{ shared: boolean }>> => {
    const response = await apiClient.post(`/dashboards/${id}/share`, { shares });
    return response.data;
  },

  // Get dashboard shares
  getShares: async (id: string): Promise<ApiResponse<ShareInfo[]>> => {
    const response = await apiClient.get(`/dashboards/${id}/shares`);
    return response.data;
  },

  // Remove share
  removeShare: async (dashboardId: string, userId: string): Promise<ApiResponse<{ removed: boolean }>> => {
    const response = await apiClient.delete(`/dashboards/${dashboardId}/shares/${userId}`);
    return response.data;
  },

  // Export dashboard
  exportDashboard: async (
    id: string,
    format: 'pdf' | 'png' | 'csv' | 'json',
    options?: { includeData?: boolean; quality?: string }
  ): Promise<ApiResponse<ExportJob>> => {
    const response = await apiClient.post(`/dashboards/${id}/export`, { format, ...options });
    return response.data;
  },

  // Get export status
  getExportStatus: async (dashboardId: string, exportId: string): Promise<ApiResponse<ExportJob>> => {
    const response = await apiClient.get(`/dashboards/${dashboardId}/export/${exportId}`);
    return response.data;
  },

  // Get dashboard versions
  getVersions: async (id: string): Promise<ApiResponse<DashboardVersion[]>> => {
    const response = await apiClient.get(`/dashboards/${id}/versions`);
    return response.data;
  },

  // Create version snapshot
  createVersion: async (id: string, description?: string): Promise<ApiResponse<DashboardVersion>> => {
    const response = await apiClient.post(`/dashboards/${id}/versions`, { description });
    return response.data;
  },

  // Restore version
  restoreVersion: async (dashboardId: string, versionId: string): Promise<ApiResponse<Dashboard>> => {
    const response = await apiClient.post(`/dashboards/${dashboardId}/versions/${versionId}/restore`);
    return response.data;
  },

  // Get dashboard analytics
  getAnalytics: async (
    id: string,
    startDate?: string,
    endDate?: string,
    granularity?: 'hour' | 'day' | 'week' | 'month'
  ): Promise<ApiResponse<DashboardAnalytics>> => {
    const params = new URLSearchParams();
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);
    if (granularity) params.append('granularity', granularity);

    const response = await apiClient.get(`/dashboards/${id}/analytics?${params.toString()}`);
    return response.data;
  },

  // Track analytics event
  trackAnalytics: async (
    id: string,
    event: { type: string; metadata?: Record<string, unknown> }
  ): Promise<ApiResponse<{ tracked: boolean }>> => {
    const response = await apiClient.post(`/dashboards/${id}/analytics/track`, event);
    return response.data;
  },

  // Get comments
  getComments: async (id: string): Promise<ApiResponse<DashboardComment[]>> => {
    const response = await apiClient.get(`/dashboards/${id}/comments`);
    return response.data;
  },

  // Add comment
  addComment: async (
    dashboardId: string,
    content: string,
    parentId?: string
  ): Promise<ApiResponse<DashboardComment>> => {
    const response = await apiClient.post(`/dashboards/${dashboardId}/comments`, { content, parentId });
    return response.data;
  },

  // Update comment
  updateComment: async (
    dashboardId: string,
    commentId: string,
    content: string
  ): Promise<ApiResponse<DashboardComment>> => {
    const response = await apiClient.put(`/dashboards/${dashboardId}/comments/${commentId}`, { content });
    return response.data;
  },

  // Delete comment
  deleteComment: async (dashboardId: string, commentId: string): Promise<ApiResponse<{ deleted: boolean }>> => {
    const response = await apiClient.delete(`/dashboards/${dashboardId}/comments/${commentId}`);
    return response.data;
  },

  // Generate embed token
  generateEmbedToken: async (
    id: string,
    options?: { expiresIn?: number; allowedDomains?: string[] }
  ): Promise<ApiResponse<{ token: string; embedUrl: string; expiresAt: string }>> => {
    const response = await apiClient.post(`/dashboards/${id}/embed`, options);
    return response.data;
  },

  // Get public dashboards
  getPublicDashboards: async (filters?: DashboardFilters): Promise<ApiResponse<Dashboard[]>> => {
    const params = new URLSearchParams();
    if (filters?.page) params.append('page', filters.page.toString());
    if (filters?.limit) params.append('limit', filters.limit.toString());
    if (filters?.search) params.append('search', filters.search);
    if (filters?.sortBy) params.append('sortBy', filters.sortBy);
    if (filters?.sortOrder) params.append('sortOrder', filters.sortOrder);

    const response = await apiClient.get(`/dashboards/public?${params.toString()}`);
    return response.data;
  },

  // Get dashboard templates
  getTemplates: async (filters?: DashboardFilters): Promise<ApiResponse<Dashboard[]>> => {
    const params = new URLSearchParams();
    if (filters?.page) params.append('page', filters.page.toString());
    if (filters?.limit) params.append('limit', filters.limit.toString());
    if (filters?.search) params.append('search', filters.search);

    const response = await apiClient.get(`/dashboards/templates?${params.toString()}`);
    return response.data;
  },
};

export default dashboardApi;
