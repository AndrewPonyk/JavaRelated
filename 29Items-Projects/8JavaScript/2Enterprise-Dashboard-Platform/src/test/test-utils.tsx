import { render, RenderOptions } from '@testing-library/react';
import { ReactElement } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '~/contexts/AuthContext';
import { DashboardProvider } from '~/contexts/DashboardContext';
import { ThemeProvider } from '~/contexts/ThemeContext';
import { ToastProvider } from '~/contexts/ToastContext';

// Mock user for testing
export const mockUser = {
  id: 'user-1',
  email: 'test@example.com',
  name: 'Test User',
  role: 'admin' as const,
  permissions: ['read', 'write', 'admin'],
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

// Mock dashboard data
export const mockDashboard = {
  id: 'dashboard-1',
  name: 'Test Dashboard',
  description: 'A test dashboard',
  layout: [],
  settings: {
    theme: 'light',
    autoRefresh: true,
    refreshInterval: 30000,
  },
  isPublic: false,
  userId: 'user-1',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

// Mock widget data
export const mockWidget = {
  id: 'widget-1',
  type: 'chart',
  title: 'Test Chart',
  config: {
    chartType: 'line',
    dataSource: 'api',
    refreshInterval: 30000,
  },
  position: { x: 0, y: 0, w: 6, h: 4 },
  dashboardId: 'dashboard-1',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

// Mock metrics data
export const mockMetrics = {
  performance: {
    cpu: 45,
    memory: 68,
    disk: 23,
    network: 156,
  },
  business: {
    revenue: 125000,
    users: 2543,
    conversion: 3.2,
    growth: 12.5,
  },
  system: {
    uptime: 99.8,
    errors: 0.2,
    latency: 45,
    throughput: 1200,
  },
};

// Create a test query client
const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  initialEntries?: string[];
  user?: typeof mockUser | null;
  queryClient?: QueryClient;
}

// Custom render function with all providers
function customRender(
  ui: ReactElement,
  {
    initialEntries = ['/'],
    user = mockUser,
    queryClient = createTestQueryClient(),
    ...renderOptions
  }: CustomRenderOptions = {}
) {
  const Wrapper = ({ children }: { children: React.ReactNode }) => (
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <ToastProvider>
            <AuthProvider initialUser={user}>
              <DashboardProvider>
                {children}
              </DashboardProvider>
            </AuthProvider>
          </ToastProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </BrowserRouter>
  );

  return render(ui, { wrapper: Wrapper, ...renderOptions });
}

// Helper to wait for loading states
export const waitForLoadingToFinish = () =>
  new Promise((resolve) => setTimeout(resolve, 0));

// Mock API responses
export const mockApiResponses = {
  auth: {
    login: {
      user: mockUser,
      accessToken: 'mock-access-token',
      refreshToken: 'mock-refresh-token',
    },
    refresh: {
      accessToken: 'new-mock-access-token',
    },
  },
  dashboards: {
    list: [mockDashboard],
    single: mockDashboard,
  },
  widgets: {
    list: [mockWidget],
    single: mockWidget,
  },
  metrics: mockMetrics,
  ml: {
    insights: [
      {
        id: 'insight-1',
        type: 'anomaly',
        title: 'CPU Spike Detected',
        description: 'Unusual CPU usage pattern detected',
        severity: 'medium',
        confidence: 0.85,
        timestamp: '2024-01-15T10:00:00Z',
        data: { metric: 'cpu', value: 95, threshold: 80 },
      },
    ],
    predictions: [
      {
        id: 'prediction-1',
        metric: 'revenue',
        timeframe: '1week',
        value: 135000,
        confidence: 0.78,
        trend: 'increasing',
        timestamp: '2024-01-15T10:00:00Z',
      },
    ],
  },
};

// Helper to create fetch mock
export const createFetchMock = (responses: Record<string, any>) => {
  return vi.fn().mockImplementation((url: string, options: any = {}) => {
    const method = options.method || 'GET';
    const key = `${method} ${url}`;

    if (responses[key]) {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(responses[key]),
        text: () => Promise.resolve(JSON.stringify(responses[key])),
      });
    }

    return Promise.resolve({
      ok: false,
      status: 404,
      json: () => Promise.resolve({ error: 'Not found' }),
    });
  });
};

// Re-export everything from testing-library
export * from '@testing-library/react';
export { default as userEvent } from '@testing-library/user-event';

// Export the custom render as default
export { customRender as render };