import React, { createContext, useContext, useEffect, useState } from 'react';
import { setupCSRFInterceptor, csrfService } from '@/services/security/csrfService';

interface CSRFContextType {
  isInitialized: boolean;
  token: string | null;
  error: string | null;
  refreshToken: () => Promise<void>;
}

const CSRFContext = createContext<CSRFContextType | undefined>(undefined);

interface CSRFProviderProps {
  children: React.ReactNode;
}

export const CSRFProvider: React.FC<CSRFProviderProps> = ({ children }) => {
  const [isInitialized, setIsInitialized] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refreshToken = React.useCallback(async () => {
    try {
      setError(null);
      const newToken = await csrfService.getValidToken();
      setToken(newToken);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to get CSRF token';
      setError(errorMessage);
      console.error('CSRF token refresh failed:', err);
    }
  }, []);

  useEffect(() => {
    const initializeCSRF = async () => {
      try {
        // Setup CSRF interceptors for API client
        setupCSRFInterceptor();

        // Get initial CSRF token
        await refreshToken();

        setIsInitialized(true);
      } catch (err) {
        console.error('Failed to initialize CSRF protection:', err);
        setError(err instanceof Error ? err.message : 'CSRF initialization failed');

        // Still mark as initialized to prevent blocking the app
        setIsInitialized(true);
      }
    };

    initializeCSRF();
  }, [refreshToken]);

  const contextValue: CSRFContextType = {
    isInitialized,
    token,
    error,
    refreshToken,
  };

  return (
    <CSRFContext.Provider value={contextValue}>
      {children}
    </CSRFContext.Provider>
  );
};

export const useCSRFContext = (): CSRFContextType => {
  const context = useContext(CSRFContext);
  if (context === undefined) {
    throw new Error('useCSRFContext must be used within a CSRFProvider');
  }
  return context;
};

// CSRF Guard Component - shows loading state until CSRF is initialized
interface CSRFGuardProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export const CSRFGuard: React.FC<CSRFGuardProps> = ({
  children,
  fallback = <div>Initializing security...</div>
}) => {
  const { isInitialized, error } = useCSRFContext();

  if (!isInitialized) {
    return <>{fallback}</>;
  }

  if (error) {
    console.warn('CSRF initialization error (continuing anyway):', error);
  }

  return <>{children}</>;
};

export default CSRFProvider;