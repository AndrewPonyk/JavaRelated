import React from 'react';
import { apiClient } from '@/services/api/apiClient';

interface CSRFTokenResponse {
  success: boolean;
  data: {
    token: string;
    expiresAt: string;
  };
  message: string;
}

class CSRFService {
  private token: string | null = null;
  private expiresAt: Date | null = null;
  private refreshPromise: Promise<string> | null = null;

  /**
   * Get current CSRF token, fetching new one if needed
   */
  public async getToken(): Promise<string> {
    // Return existing token if still valid
    if (this.token && this.expiresAt && new Date() < this.expiresAt) {
      return this.token;
    }

    // If already refreshing, wait for it
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    // Fetch new token
    this.refreshPromise = this.fetchNewToken();

    try {
      const token = await this.refreshPromise;
      return token;
    } finally {
      this.refreshPromise = null;
    }
  }

  /**
   * Fetch new CSRF token from server
   */
  private async fetchNewToken(): Promise<string> {
    try {
      const response = await fetch('/api/auth/csrf-token', {
        method: 'GET',
        credentials: 'include', // Include cookies
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch CSRF token: ${response.status}`);
      }

      const data: CSRFTokenResponse = await response.json();

      this.token = data.data.token;
      this.expiresAt = new Date(data.data.expiresAt);

      return this.token;
    } catch (error) {
      console.error('Error fetching CSRF token:', error);
      throw new Error('Failed to get CSRF token');
    }
  }

  /**
   * Refresh CSRF token
   */
  public async refreshToken(): Promise<string> {
    try {
      const response = await fetch('/api/auth/csrf-token/refresh', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-Token': this.token || '',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to refresh CSRF token: ${response.status}`);
      }

      const data: CSRFTokenResponse = await response.json();

      this.token = data.data.token;
      this.expiresAt = new Date(data.data.expiresAt);

      return this.token;
    } catch (error) {
      console.error('Error refreshing CSRF token:', error);
      // Fallback to fetching new token
      return this.fetchNewToken();
    }
  }

  /**
   * Clear stored token (e.g., on logout)
   */
  public clearToken(): void {
    this.token = null;
    this.expiresAt = null;
  }

  /**
   * Check if current token is expired or will expire soon
   */
  public isTokenExpired(bufferMinutes: number = 5): boolean {
    if (!this.expiresAt) return true;

    const bufferTime = new Date(Date.now() + bufferMinutes * 60 * 1000);
    return bufferTime >= this.expiresAt;
  }

  /**
   * Get token with automatic refresh if expired
   */
  public async getValidToken(): Promise<string> {
    if (this.isTokenExpired()) {
      return this.refreshToken();
    }

    return this.getToken();
  }
}

// Create singleton instance
export const csrfService = new CSRFService();

// CSRF-aware fetch wrapper
export async function csrfFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const csrfToken = await csrfService.getValidToken();

  const headers = {
    'Content-Type': 'application/json',
    'X-CSRF-Token': csrfToken,
    ...options.headers,
  };

  return fetch(url, {
    ...options,
    credentials: 'include',
    headers,
  });
}

// Axios interceptor for CSRF tokens
export function setupCSRFInterceptor() {
  // Request interceptor to add CSRF token
  apiClient.interceptors.request.use(
    async (config) => {
      // Only add CSRF token for state-changing methods
      const methodsRequiringCSRF = ['post', 'put', 'delete', 'patch'];

      if (methodsRequiringCSRF.includes(config.method?.toLowerCase() || '')) {
        try {
          const token = await csrfService.getValidToken();
          config.headers['X-CSRF-Token'] = token;
        } catch (error) {
          console.error('Failed to get CSRF token for request:', error);
          // Continue with request without token - server will handle the error
        }
      }

      return config;
    },
    (error) => Promise.reject(error)
  );

  // Response interceptor to handle CSRF errors
  apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config;

      // If CSRF error and we haven't already retried
      if (
        error.response?.status === 400 &&
        error.response?.data?.error?.code === 'VALIDATION_ERROR' &&
        error.response?.data?.error?.message?.includes('CSRF') &&
        !originalRequest._csrfRetried
      ) {
        originalRequest._csrfRetried = true;

        try {
          // Refresh CSRF token and retry request
          const newToken = await csrfService.refreshToken();
          originalRequest.headers['X-CSRF-Token'] = newToken;

          return apiClient(originalRequest);
        } catch (refreshError) {
          console.error('Failed to refresh CSRF token:', refreshError);
          return Promise.reject(error);
        }
      }

      return Promise.reject(error);
    }
  );
}

// React hook for CSRF token management
export function useCSRF() {
  const [token, setToken] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const refreshToken = React.useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const newToken = await csrfService.getValidToken();
      setToken(newToken);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to get CSRF token';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    refreshToken();
  }, [refreshToken]);

  return {
    token,
    loading,
    error,
    refresh: refreshToken,
    clearToken: () => {
      csrfService.clearToken();
      setToken(null);
    },
  };
}

export default csrfService;