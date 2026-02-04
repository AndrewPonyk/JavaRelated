import { AxiosRequestConfig, AxiosResponse } from 'axios';
import { z } from 'zod';
import { apiClient } from './apiClient';

// Types
export interface User {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role: 'SUPER_ADMIN' | 'ADMIN' | 'MANAGER' | 'USER' | 'VIEWER';
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

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export interface AuthResponse {
  success: boolean;
  data: {
    user: User;
    tokens: TokenPair;
  };
  message: string;
}

export interface ApiResponse<T = any> {
  success: boolean;
  data: T;
  message?: string;
  error?: {
    code: string;
    message: string;
    details?: any;
  };
}

// Validation schemas
export const loginSchema = z.object({
  email: z.string().email('Invalid email format'),
  password: z.string().min(1, 'Password is required'),
});

export const registerSchema = z.object({
  email: z.string().email('Invalid email format'),
  password: z.string()
    .min(8, 'Password must be at least 8 characters long')
    .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'Password must contain at least one lowercase letter, one uppercase letter, and one number'),
  firstName: z.string().optional(),
  lastName: z.string().optional(),
});

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Current password is required'),
  newPassword: z.string()
    .min(8, 'New password must be at least 8 characters long')
    .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'New password must contain at least one lowercase letter, one uppercase letter, and one number'),
});

export const forgotPasswordSchema = z.object({
  email: z.string().email('Invalid email format'),
});

export const resetPasswordSchema = z.object({
  token: z.string().min(1, 'Reset token is required'),
  newPassword: z.string()
    .min(8, 'New password must be at least 8 characters long')
    .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'New password must contain at least one lowercase letter, one uppercase letter, and one number'),
});

// Auth API class
class AuthApi {
  private currentInterceptorId: number | null = null;

  // Set authorization header
  setAuthHeader(token: string): void {
    apiClient.defaults.headers.common['Authorization'] = `Bearer ${token}`;
  }

  // Clear authorization header
  clearAuthHeader(): void {
    delete apiClient.defaults.headers.common['Authorization'];
  }

  // Login user
  async login(email: string, password: string): Promise<AuthResponse> {
    const validatedData = loginSchema.parse({ email, password });
    const response = await apiClient.post<AuthResponse>('/auth/login', validatedData);
    return response.data;
  }

  // Register user
  async register(data: {
    email: string;
    password: string;
    firstName?: string;
    lastName?: string;
  }): Promise<AuthResponse> {
    const validatedData = registerSchema.parse(data);
    const response = await apiClient.post<AuthResponse>('/auth/register', validatedData);
    return response.data;
  }

  // Logout user
  async logout(): Promise<ApiResponse> {
    const response = await apiClient.post<ApiResponse>('/auth/logout');
    return response.data;
  }

  // Get current user
  async getCurrentUser(): Promise<User> {
    const response = await apiClient.get<ApiResponse<{ user: User }>>('/auth/me');
    return response.data.data.user;
  }

  // Refresh tokens
  async refreshToken(refreshToken: string): Promise<ApiResponse<{ tokens: TokenPair }>> {
    const response = await apiClient.post<ApiResponse<{ tokens: TokenPair }>>('/auth/refresh', {
      refreshToken,
    });
    return response.data;
  }

  // Verify token
  async verifyToken(): Promise<ApiResponse<{ user: User }>> {
    const response = await apiClient.post<ApiResponse<{ user: User }>>('/auth/verify');
    return response.data;
  }

  // Change password
  async changePassword(currentPassword: string, newPassword: string): Promise<ApiResponse> {
    const validatedData = changePasswordSchema.parse({ currentPassword, newPassword });
    const response = await apiClient.post<ApiResponse>('/auth/change-password', validatedData);
    return response.data;
  }

  // Forgot password
  async forgotPassword(email: string): Promise<ApiResponse> {
    const validatedData = forgotPasswordSchema.parse({ email });
    const response = await apiClient.post<ApiResponse>('/auth/forgot-password', validatedData);
    return response.data;
  }

  // Reset password
  async resetPassword(token: string, newPassword: string): Promise<ApiResponse> {
    const validatedData = resetPasswordSchema.parse({ token, newPassword });
    const response = await apiClient.post<ApiResponse>('/auth/reset-password', validatedData);
    return response.data;
  }

  // Setup token refresh interceptor
  setupTokenRefreshInterceptor(
    onTokenRefresh: () => Promise<void>,
    onRefreshFailed: () => void
  ): number {
    // Clear existing interceptor
    if (this.currentInterceptorId !== null) {
      apiClient.interceptors.response.eject(this.currentInterceptorId);
    }

    // Add new interceptor
    this.currentInterceptorId = apiClient.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;

        // Check if error is 401 and we haven't already tried to refresh
        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;

          try {
            await onTokenRefresh();
            // Retry original request with new token
            return apiClient(originalRequest);
          } catch (refreshError) {
            // Refresh failed, redirect to login
            onRefreshFailed();
            return Promise.reject(refreshError);
          }
        }

        return Promise.reject(error);
      }
    );

    return this.currentInterceptorId;
  }

  // Remove interceptor
  removeInterceptor(interceptorId: number): void {
    apiClient.interceptors.response.eject(interceptorId);
    if (this.currentInterceptorId === interceptorId) {
      this.currentInterceptorId = null;
    }
  }

  // Generic API request method
  async request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response = await apiClient.request<ApiResponse<T>>(config);
    return response.data;
  }

  // Health check
  async healthCheck(): Promise<ApiResponse> {
    const response = await apiClient.get<ApiResponse>('/auth/health');
    return response.data;
  }

  // Get API client instance (for advanced usage)
  getApiClient() {
    return apiClient;
  }
}

// Create and export auth API instance
export const authApi = new AuthApi();

// Default export
export default authApi;