import { apiClient } from './apiClient';

// Types
export interface User {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role: UserRole;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  lastLogin?: string;
  profile?: UserProfile;
}

export interface UserProfile {
  id: string;
  userId: string;
  theme: 'light' | 'dark' | 'auto';
  timezone: string;
  language: string;
  notifications: Record<string, boolean>;
  avatar?: string;
  bio?: string;
}

export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'MANAGER' | 'USER' | 'VIEWER';

export interface CreateUserData {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
  role?: UserRole;
  isActive?: boolean;
}

export interface UpdateUserData {
  email?: string;
  firstName?: string;
  lastName?: string;
  role?: UserRole;
  isActive?: boolean;
}

export interface GetUsersParams {
  page?: number;
  limit?: number;
  search?: string;
  role?: UserRole;
  isActive?: boolean;
  sortBy?: 'createdAt' | 'updatedAt' | 'email' | 'lastName';
  sortOrder?: 'asc' | 'desc';
}

// Backend response structure
interface BackendPaginatedData<T> {
  data: T[];
  total: number;
  page: number;
  limit: number;
  pages: number;
  hasNext: boolean;
  hasPrev: boolean;
}

// Transformed response for frontend consumption
export interface PaginatedResponse<T> {
  success: boolean;
  data: {
    users: T[];
    pagination: {
      page: number;
      limit: number;
      total: number;
      totalPages: number;
    };
  };
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

// User API functions
export const userApi = {
  // Get all users with pagination and filtering
  async getUsers(params: GetUsersParams = {}): Promise<PaginatedResponse<User>> {
    const queryParams = new URLSearchParams();
    if (params.page) queryParams.append('page', params.page.toString());
    if (params.limit) queryParams.append('limit', params.limit.toString());
    if (params.search) queryParams.append('search', params.search);
    if (params.role) queryParams.append('role', params.role);
    if (params.isActive !== undefined) queryParams.append('isActive', params.isActive.toString());
    if (params.sortBy) queryParams.append('sortBy', params.sortBy);
    if (params.sortOrder) queryParams.append('sortOrder', params.sortOrder);

    const response = await apiClient.get<{ success: boolean; data: BackendPaginatedData<User> }>(`/users?${queryParams.toString()}`);

    // Transform backend response to frontend format
    const backendData = response.data.data;
    return {
      success: response.data.success,
      data: {
        users: backendData.data,
        pagination: {
          page: backendData.page,
          limit: backendData.limit,
          total: backendData.total,
          totalPages: backendData.pages,
        },
      },
    };
  },

  // Get single user by ID
  async getUser(id: string): Promise<ApiResponse<{ user: User }>> {
    const response = await apiClient.get<ApiResponse<{ user: User }>>(`/users/${id}`);
    return response.data;
  },

  // Create new user
  async createUser(data: CreateUserData): Promise<ApiResponse<{ user: User }>> {
    const response = await apiClient.post<ApiResponse<{ user: User }>>('/users', data);
    return response.data;
  },

  // Update user
  async updateUser(id: string, data: UpdateUserData): Promise<ApiResponse<{ user: User }>> {
    const response = await apiClient.put<ApiResponse<{ user: User }>>(`/users/${id}`, data);
    return response.data;
  },

  // Delete user (soft delete)
  async deleteUser(id: string): Promise<ApiResponse<null>> {
    const response = await apiClient.delete<ApiResponse<null>>(`/users/${id}`);
    return response.data;
  },

  // Search users
  async searchUsers(query: string, limit = 10): Promise<ApiResponse<{ users: User[] }>> {
    const response = await apiClient.get<ApiResponse<{ users: User[] }>>(
      `/users/search?q=${encodeURIComponent(query)}&limit=${limit}`
    );
    return response.data;
  },
};

export default userApi;
