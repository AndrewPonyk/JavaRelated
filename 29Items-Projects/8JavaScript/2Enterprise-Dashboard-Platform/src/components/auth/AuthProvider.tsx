import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi, type User } from '@/services/api/authApi';
import { queryKeys, invalidateQueries } from '@/services/cache/queryClient';
import { toast } from '@/hooks/ui/useToast';

// Token management
interface TokenStorage {
  accessToken: string | null;
  refreshToken: string | null;
}

// Auth context types
interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (data: RegisterData) => Promise<void>;
  logout: () => void;
  refreshToken: () => Promise<void>;
  updateUser: (user: User) => void;
}

interface RegisterData {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

// Create auth context
const AuthContext = createContext<AuthContextType | null>(null);

// Hook to use auth context
export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

// Token storage utilities
const TOKEN_STORAGE_KEY = 'auth_tokens';

const getStoredTokens = (): TokenStorage => {
  try {
    const stored = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (stored) {
      return JSON.parse(stored);
    }
  } catch (error) {
    console.error('Failed to parse stored tokens:', error);
  }
  return { accessToken: null, refreshToken: null };
};

const setStoredTokens = (tokens: TokenStorage): void => {
  try {
    localStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(tokens));
  } catch (error) {
    console.error('Failed to store tokens:', error);
  }
};

const clearStoredTokens = (): void => {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
};

// Initialize auth header synchronously on module load (runs immediately when module is imported)
const storedTokens = getStoredTokens();
if (storedTokens.accessToken) {
  authApi.setAuthHeader(storedTokens.accessToken);
}

// Auth provider component
export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isInitializing] = useState(false); // Already initialized at module load
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

  // Get current user query - only runs after auth header is set
  const {
    data: user,
    isLoading: isUserLoading,
    error: userError,
    refetch: refetchUser,
  } = useQuery({
    queryKey: queryKeys.auth.user(),
    queryFn: authApi.getCurrentUser,
    enabled: !isInitializing && !!getStoredTokens().accessToken,
    retry: false,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  const isAuthenticated = !!user && !userError;
  const isLoading = isUserLoading || isInitializing;

  // Handle token expiration
  useEffect(() => {
    if (userError && (userError as any)?.status === 401) {
      handleLogout();
    }
  }, [userError]);

  // Login function
  const login = useCallback(async (email: string, password: string): Promise<void> => {
    try {
      const response = await authApi.login(email, password);
      const { user: userData, tokens } = response.data;

      // Store tokens
      setStoredTokens(tokens);
      authApi.setAuthHeader(tokens.accessToken);

      // Update query cache
      queryClient.setQueryData(queryKeys.auth.user(), userData);

      // Navigate to intended page or dashboard
      const from = (location.state as any)?.from?.pathname || '/dashboard';
      navigate(from, { replace: true });

      toast({
        title: 'Welcome back!',
        description: `Successfully logged in as ${userData.firstName || userData.email}`,
      });

    } catch (error: any) {
      console.error('Login failed:', error);
      throw new Error(error?.data?.message || 'Login failed. Please check your credentials.');
    }
  }, [navigate, location, queryClient]);

  // Register function
  const register = useCallback(async (data: RegisterData): Promise<void> => {
    try {
      const response = await authApi.register(data);
      const { user: userData, tokens } = response.data;

      // Store tokens
      setStoredTokens(tokens);
      authApi.setAuthHeader(tokens.accessToken);

      // Update query cache
      queryClient.setQueryData(queryKeys.auth.user(), userData);

      // Navigate to dashboard
      navigate('/dashboard', { replace: true });

      toast({
        title: 'Welcome!',
        description: 'Account created successfully. You are now logged in.',
      });

    } catch (error: any) {
      console.error('Registration failed:', error);
      throw new Error(error?.data?.message || 'Registration failed. Please try again.');
    }
  }, [navigate, queryClient]);

  // Logout function
  const logout = useCallback((): void => {
    handleLogout();
  }, []);

  // Handle logout (internal function)
  const handleLogout = useCallback((): void => {
    // Clear tokens
    clearStoredTokens();
    authApi.clearAuthHeader();

    // Clear query cache
    queryClient.clear();

    // Navigate to login
    navigate('/auth/login', { replace: true });

    toast({
      title: 'Logged out',
      description: 'You have been successfully logged out.',
    });
  }, [navigate, queryClient]);

  // Refresh token function
  const refreshToken = useCallback(async (): Promise<void> => {
    try {
      const tokens = getStoredTokens();
      if (!tokens.refreshToken) {
        throw new Error('No refresh token available');
      }

      const response = await authApi.refreshToken(tokens.refreshToken);
      const newTokens = response.data.tokens;

      // Store new tokens
      setStoredTokens(newTokens);
      authApi.setAuthHeader(newTokens.accessToken);

      // Refetch user data
      refetchUser();

    } catch (error) {
      console.error('Token refresh failed:', error);
      handleLogout();
    }
  }, [refetchUser, handleLogout]);

  // Update user function (for profile updates)
  const updateUser = useCallback((updatedUser: User): void => {
    queryClient.setQueryData(queryKeys.auth.user(), updatedUser);
  }, [queryClient]);

  // Auto-refresh token before expiration
  useEffect(() => {
    if (!isAuthenticated) return;

    // Set up token refresh interval (refresh 5 minutes before expiration)
    const refreshInterval = setInterval(() => {
      const tokens = getStoredTokens();
      if (tokens.accessToken) {
        try {
          const payload = JSON.parse(atob(tokens.accessToken.split('.')[1]));
          const expirationTime = payload.exp * 1000;
          const now = Date.now();
          const timeUntilExpiration = expirationTime - now;

          // Refresh if token expires in the next 5 minutes
          if (timeUntilExpiration < 5 * 60 * 1000) {
            refreshToken();
          }
        } catch (error) {
          console.error('Failed to parse token for refresh:', error);
        }
      }
    }, 60 * 1000); // Check every minute

    return () => clearInterval(refreshInterval);
  }, [isAuthenticated, refreshToken]);

  // Set up axios interceptor for token refresh
  useEffect(() => {
    const interceptor = authApi.setupTokenRefreshInterceptor(refreshToken, handleLogout);
    return () => {
      authApi.removeInterceptor(interceptor);
    };
  }, [refreshToken, handleLogout]);

  // Context value
  const value: AuthContextType = {
    user: user || null,
    isAuthenticated,
    isLoading,
    login,
    register,
    logout,
    refreshToken,
    updateUser,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

// Protected route component
interface ProtectedRouteProps {
  children: React.ReactNode;
  roles?: string[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, roles }) => {
  const { isAuthenticated, isLoading, user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      // Redirect to login with return URL
      navigate('/auth/login', {
        state: { from: location },
        replace: true,
      });
    }
  }, [isAuthenticated, isLoading, navigate, location]);

  useEffect(() => {
    if (isAuthenticated && user && roles && roles.length > 0) {
      // Check if user has required role
      if (!roles.includes(user.role)) {
        toast({
          title: 'Access Denied',
          description: 'You do not have permission to access this page.',
          variant: 'destructive',
        });
        navigate('/dashboard', { replace: true });
      }
    }
  }, [isAuthenticated, user, roles, navigate]);

  // Show loading state
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  // Show children if authenticated and authorized
  if (isAuthenticated && user) {
    if (!roles || roles.length === 0 || roles.includes(user.role)) {
      return <>{children}</>;
    }
  }

  // Return null while redirecting
  return null;
};