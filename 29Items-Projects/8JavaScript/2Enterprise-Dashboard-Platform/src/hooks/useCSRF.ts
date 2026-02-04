import { useState, useCallback, useEffect } from 'react';
import { csrfService } from '@/services/security/csrfService';
import { useCSRFContext } from '@/components/security/CSRFProvider';

interface UseCSRFReturn {
  token: string | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  getToken: () => Promise<string>;
  withCSRF: <T extends Record<string, any>>(data: T) => T & { _csrf: string };
}

/**
 * Hook for CSRF token management in components
 */
export function useCSRF(): UseCSRFReturn {
  const { token: contextToken, refreshToken: contextRefresh } = useCSRFContext();
  const [token, setToken] = useState<string | null>(contextToken);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Update local token when context token changes
  useEffect(() => {
    setToken(contextToken);
  }, [contextToken]);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      await contextRefresh();
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to refresh CSRF token';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  }, [contextRefresh]);

  const getToken = useCallback(async (): Promise<string> => {
    try {
      const currentToken = await csrfService.getValidToken();
      setToken(currentToken);
      return currentToken;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to get CSRF token';
      setError(errorMessage);
      throw new Error(errorMessage);
    }
  }, []);

  const withCSRF = useCallback(<T extends Record<string, any>>(data: T): T & { _csrf: string } => {
    if (!token) {
      throw new Error('CSRF token not available');
    }
    return { ...data, _csrf: token };
  }, [token]);

  return {
    token,
    loading,
    error,
    refresh,
    getToken,
    withCSRF,
  };
}

/**
 * Hook for CSRF-protected form submissions
 */
export function useCSRFForm<T = any>() {
  const { getToken, withCSRF } = useCSRF();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submitWithCSRF = useCallback(async (
    submitFn: (data: T) => Promise<any>,
    data: T
  ) => {
    setLoading(true);
    setError(null);

    try {
      // Ensure we have a fresh token
      await getToken();

      // Add CSRF token to data and submit
      const protectedData = withCSRF(data as Record<string, any>) as T;
      const result = await submitFn(protectedData);

      return result;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Form submission failed';
      setError(errorMessage);
      throw err;
    } finally {
      setLoading(false);
    }
  }, [getToken, withCSRF]);

  return {
    submitWithCSRF,
    loading,
    error,
    clearError: () => setError(null),
  };
}

/**
 * Hook for CSRF-protected API calls
 */
export function useCSRFApi() {
  const { getToken } = useCSRF();

  const callWithCSRF = useCallback(async (
    apiFn: (token: string) => Promise<any>
  ) => {
    const token = await getToken();
    return apiFn(token);
  }, [getToken]);

  return { callWithCSRF };
}

export default useCSRF;