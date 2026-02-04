import axios, { AxiosInstance } from 'axios';

// Base API configuration
// Always use relative URL - Vite proxy (dev) or nginx (prod) handles routing to backend
// VITE_API_URL is for Vite server proxy config only, not for browser requests

// Create axios instance
export const apiClient: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 60000, // Increased to 60s for debugging
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add metadata
apiClient.interceptors.request.use(
  (config) => {
    // Add request timestamp for logging
    config.metadata = { startTime: Date.now() };
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor for error handling and logging
apiClient.interceptors.response.use(
  (response) => {
    // Log successful requests in development
    if (import.meta.env.DEV) {
      const duration = Date.now() - (response.config.metadata?.startTime || 0);
      console.log(`API Success: ${response.config.method?.toUpperCase()} ${response.config.url} (${duration}ms)`);
    }
    return response;
  },
  (error) => {
    // Log errors in development
    if (import.meta.env.DEV) {
      const duration = error.config?.metadata?.startTime ? Date.now() - error.config.metadata.startTime : 0;
      console.error(`API Error: ${error.config?.method?.toUpperCase()} ${error.config?.url} (${duration}ms)`, error);
    }

    // Enhance error with additional information
    const enhancedError = {
      ...error,
      status: error.response?.status,
      data: error.response?.data,
      message: error.response?.data?.message || error.message,
    };

    return Promise.reject(enhancedError);
  }
);

export default apiClient;
