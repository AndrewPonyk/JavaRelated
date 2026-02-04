import { QueryClient, QueryCache, MutationCache } from '@tanstack/react-query';
import { toast } from '@/hooks/ui/useToast';

// Default query options
const defaultQueryOptions = {
  queries: {
    staleTime: 5 * 60 * 1000, // 5 minutes
    cacheTime: 10 * 60 * 1000, // 10 minutes
    retry: (failureCount: number, error: any) => {
      // Don't retry on 4xx errors except 408, 429
      if (error?.status >= 400 && error?.status < 500 && ![408, 429].includes(error.status)) {
        return false;
      }

      // Retry up to 3 times for other errors
      return failureCount < 3;
    },
    retryDelay: (attemptIndex: number) => Math.min(1000 * 2 ** attemptIndex, 30000),
    refetchOnWindowFocus: false,
    refetchOnReconnect: true,
    refetchOnMount: true,
  },
  mutations: {
    retry: (failureCount: number, error: any) => {
      // Don't retry mutations on client errors
      if (error?.status >= 400 && error?.status < 500) {
        return false;
      }

      // Retry once for server errors
      return failureCount < 1;
    },
  },
};

// Query cache with global error handling
const queryCache = new QueryCache({
  onError: (error: any, query) => {
    // Log error for debugging
    console.error('Query Error:', {
      error,
      queryKey: query.queryKey,
      queryHash: query.queryHash,
    });

    // Show user-friendly error messages for specific cases
    if (error?.status === 401) {
      // Authentication error - handled by auth provider
      return;
    }

    if (error?.status === 403) {
      toast({
        title: 'Access Denied',
        description: 'You do not have permission to access this resource.',
        variant: 'destructive',
      });
      return;
    }

    if (error?.status === 404) {
      toast({
        title: 'Not Found',
        description: 'The requested resource could not be found.',
        variant: 'destructive',
      });
      return;
    }

    if (error?.status >= 500) {
      toast({
        title: 'Server Error',
        description: 'A server error occurred. Please try again later.',
        variant: 'destructive',
      });
      return;
    }

    // Generic error message for unhandled cases
    if (error?.message) {
      toast({
        title: 'Error',
        description: error.message,
        variant: 'destructive',
      });
    }
  },
});

// Mutation cache with global error handling
const mutationCache = new MutationCache({
  onError: (error: any, variables, context, mutation) => {
    console.error('Mutation Error:', {
      error,
      mutationKey: mutation.options.mutationKey,
      variables,
    });

    // Don't show error toast if the mutation handles it explicitly
    if (mutation.options.meta?.suppressErrorToast) {
      return;
    }

    // Handle specific error cases
    if (error?.status === 401) {
      return; // Handled by auth provider
    }

    if (error?.status === 403) {
      toast({
        title: 'Access Denied',
        description: 'You do not have permission to perform this action.',
        variant: 'destructive',
      });
      return;
    }

    if (error?.status === 409) {
      toast({
        title: 'Conflict',
        description: error?.message || 'A conflict occurred while processing your request.',
        variant: 'destructive',
      });
      return;
    }

    if (error?.status === 422) {
      // Validation error
      const validationMessage = error?.data?.message || 'Please check your input and try again.';
      toast({
        title: 'Validation Error',
        description: validationMessage,
        variant: 'destructive',
      });
      return;
    }

    if (error?.status >= 500) {
      toast({
        title: 'Server Error',
        description: 'An unexpected error occurred. Please try again.',
        variant: 'destructive',
      });
      return;
    }

    // Generic error
    toast({
      title: 'Error',
      description: error?.message || 'An error occurred while processing your request.',
      variant: 'destructive',
    });
  },

  onSuccess: (data, variables, context, mutation) => {
    // Show success toast for mutations that specify it
    if (mutation.options.meta?.showSuccessToast) {
      const message = mutation.options.meta.successMessage || 'Operation completed successfully!';
      toast({
        title: 'Success',
        description: message,
        variant: 'default',
      });
    }
  },
});

// Create query client
export const queryClient = new QueryClient({
  queryCache,
  mutationCache,
  defaultOptions: defaultQueryOptions,
});

// Query key factory for consistent key generation
export const queryKeys = {
  // Auth
  auth: {
    user: () => ['auth', 'user'] as const,
    profile: () => ['auth', 'profile'] as const,
  },

  // Users
  users: {
    all: () => ['users'] as const,
    lists: () => [...queryKeys.users.all(), 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.users.lists(), filters] as const,
    details: () => [...queryKeys.users.all(), 'detail'] as const,
    detail: (id: string) => [...queryKeys.users.details(), id] as const,
    search: (query: string) => ['users', 'search', query] as const,
    stats: (id: string) => ['users', 'stats', id] as const,
  },

  // Dashboards
  dashboards: {
    all: () => ['dashboards'] as const,
    lists: () => [...queryKeys.dashboards.all(), 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.dashboards.lists(), filters] as const,
    details: () => [...queryKeys.dashboards.all(), 'detail'] as const,
    detail: (id: string) => [...queryKeys.dashboards.details(), id] as const,
    analytics: (id: string, params?: Record<string, any>) =>
      ['dashboards', id, 'analytics', params] as const,
    shares: (id: string) => ['dashboards', id, 'shares'] as const,
    versions: (id: string) => ['dashboards', id, 'versions'] as const,
    comments: (id: string) => ['dashboards', id, 'comments'] as const,
    export: (id: string, exportId: string) => ['dashboards', id, 'export', exportId] as const,
    public: (filters?: Record<string, any>) => ['dashboards', 'public', filters] as const,
    templates: (filters?: Record<string, any>) => ['dashboards', 'templates', filters] as const,
  },

  // Widgets
  widgets: {
    all: () => ['widgets'] as const,
    lists: () => [...queryKeys.widgets.all(), 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.widgets.lists(), filters] as const,
    byDashboard: (dashboardId: string) => ['widgets', 'dashboard', dashboardId] as const,
    detail: (id: string) => ['widgets', 'detail', id] as const,
    data: (id: string) => ['widgets', 'data', id] as const,
    shared: (filters?: Record<string, any>) => ['widgets', 'shared', filters] as const,
    templates: (filters?: Record<string, any>) => ['widgets', 'templates', filters] as const,
  },

  // Search
  search: {
    results: (query: string, type?: string) => ['search', query, type] as const,
    suggestions: (query: string) => ['search', 'suggestions', query] as const,
    recent: () => ['search', 'recent'] as const,
  },

  // Settings
  settings: {
    user: () => ['settings', 'user'] as const,
    system: () => ['settings', 'system'] as const,
    systemSection: (section: string) => ['settings', 'system', section] as const,
  },

  // Analytics
  analytics: {
    all: () => ['analytics'] as const,
    dashboard: (id: string, params: Record<string, any>) =>
      ['analytics', 'dashboard', id, params] as const,
    user: (id: string, params: Record<string, any>) =>
      ['analytics', 'user', id, params] as const,
  },

  // Data Connections
  dataConnections: {
    all: () => ['dataConnections'] as const,
    lists: () => [...queryKeys.dataConnections.all(), 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.dataConnections.lists(), filters] as const,
    details: () => [...queryKeys.dataConnections.all(), 'detail'] as const,
    detail: (id: string) => [...queryKeys.dataConnections.details(), id] as const,
    schema: (id: string) => ['dataConnections', id, 'schema'] as const,
    tables: (id: string, schema?: string) => ['dataConnections', id, 'tables', schema] as const,
    columns: (id: string, table: string, schema?: string) =>
      ['dataConnections', id, 'columns', table, schema] as const,
    queries: (id: string) => ['dataConnections', id, 'queries'] as const,
    queryResult: (id: string, queryHash: string) =>
      ['dataConnections', id, 'result', queryHash] as const,
  },
} as const;

// Utility functions for cache management
export const invalidateQueries = {
  // User-related invalidations
  user: (userId?: string) => {
    if (userId) {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.detail(userId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.users.stats(userId) });
    }
    queryClient.invalidateQueries({ queryKey: queryKeys.users.lists() });
  },

  // Dashboard-related invalidations
  dashboard: (dashboardId?: string) => {
    if (dashboardId) {
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboards.detail(dashboardId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.widgets.byDashboard(dashboardId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboards.analytics(dashboardId) });
    }
    queryClient.invalidateQueries({ queryKey: queryKeys.dashboards.lists() });
  },

  // Widget-related invalidations
  widget: (widgetId: string, dashboardId?: string) => {
    queryClient.invalidateQueries({ queryKey: queryKeys.widgets.detail(widgetId) });
    queryClient.invalidateQueries({ queryKey: queryKeys.widgets.data(widgetId) });
    if (dashboardId) {
      queryClient.invalidateQueries({ queryKey: queryKeys.widgets.byDashboard(dashboardId) });
    }
  },

  // Auth-related invalidations
  auth: () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.auth.user() });
    queryClient.invalidateQueries({ queryKey: queryKeys.auth.profile() });
  },

  // Data Connection-related invalidations
  dataConnection: (connectionId?: string) => {
    if (connectionId) {
      queryClient.invalidateQueries({ queryKey: queryKeys.dataConnections.detail(connectionId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.dataConnections.schema(connectionId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.dataConnections.queries(connectionId) });
      // Invalidate all table/column queries for this connection
      queryClient.invalidateQueries({
        predicate: (query) =>
          query.queryKey[0] === 'dataConnections' && query.queryKey[1] === connectionId,
      });
    }
    queryClient.invalidateQueries({ queryKey: queryKeys.dataConnections.lists() });
  },

  // Clear all cache
  all: () => {
    queryClient.clear();
  },
};

// Pre-populate cache utilities
export const populateCache = {
  user: (user: any) => {
    queryClient.setQueryData(queryKeys.auth.user(), user);
    queryClient.setQueryData(queryKeys.users.detail(user.id), user);
  },

  dashboard: (dashboard: any) => {
    queryClient.setQueryData(queryKeys.dashboards.detail(dashboard.id), dashboard);

    // Update lists cache if it exists
    queryClient.setQueryData(
      queryKeys.dashboards.lists(),
      (oldData: any) => {
        if (!oldData) return oldData;

        const existingIndex = oldData.data?.findIndex((d: any) => d.id === dashboard.id);
        if (existingIndex >= 0) {
          const newData = [...oldData.data];
          newData[existingIndex] = dashboard;
          return { ...oldData, data: newData };
        }
        return oldData;
      }
    );
  },
};

// Performance monitoring
if (process.env.NODE_ENV === 'development') {
  // Log slow queries in development
  queryCache.subscribe((event) => {
    if (event.type === 'observerResultsUpdated') {
      const query = event.query;
      if (query.state.fetchStatus === 'idle' && query.state.dataUpdatedAt > 0) {
        const duration = Date.now() - query.state.dataUpdatedAt;
        if (duration > 5000) { // 5 seconds
          console.warn('Slow Query Detected:', {
            queryKey: query.queryKey,
            duration: `${duration}ms`,
          });
        }
      }
    }
  });
}

// Export default query client
export default queryClient;