/**
 * Authentication API service
 */
import api, { setAuthTokens, clearAuthTokens } from './api';
import type { AuthTokens, LoginCredentials, RegisterData, User } from '../types';

export const authService = {
  /**
   * Login user
   */
  async login(credentials: LoginCredentials): Promise<{ user: User; tokens: AuthTokens }> {
    const response = await api.post<AuthTokens>('/auth/login/', credentials);
    setAuthTokens(response.data);

    // Fetch user profile
    const userResponse = await api.get<User>('/auth/profile/');

    return {
      user: userResponse.data,
      tokens: response.data,
    };
  },

  /**
   * Register new user
   */
  async register(data: RegisterData): Promise<User> {
    const response = await api.post<User>('/auth/register/', data);
    return response.data;
  },

  /**
   * Logout user
   */
  async logout(): Promise<void> {
    clearAuthTokens();
    // TODO: Call backend logout endpoint to blacklist refresh token
  },

  /**
   * Get current user profile
   */
  async getProfile(): Promise<User> {
    const response = await api.get<User>('/auth/profile/');
    return response.data;
  },

  /**
   * Update user profile
   */
  async updateProfile(data: Partial<User>): Promise<User> {
    const response = await api.patch<User>('/auth/profile/', data);
    return response.data;
  },

  /**
   * Change password
   */
  async changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await api.put('/auth/password/change/', {
      old_password: oldPassword,
      new_password: newPassword,
    });
  },

  /**
   * Refresh access token
   */
  async refreshToken(refreshToken: string): Promise<AuthTokens> {
    const response = await api.post<AuthTokens>('/auth/refresh/', {
      refresh: refreshToken,
    });
    setAuthTokens(response.data);
    return response.data;
  },
};
