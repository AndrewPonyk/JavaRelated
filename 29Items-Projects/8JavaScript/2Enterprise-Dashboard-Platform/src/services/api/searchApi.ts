import { apiClient } from './apiClient';

// Types
export interface SearchResult {
  id: string;
  type: 'dashboard' | 'widget' | 'user' | 'page';
  title: string;
  description?: string;
  url: string;
  icon?: string;
  metadata?: Record<string, any>;
}

export interface SearchResponse {
  success: boolean;
  data: SearchResult[];
  meta: {
    query: string;
    type: string;
    total: number;
  };
  message: string;
}

export type SearchType = 'all' | 'dashboards' | 'widgets' | 'users' | 'pages';

// API functions
export const searchApi = {
  // Global search
  search: async (query: string, type: SearchType = 'all', limit: number = 10): Promise<SearchResponse> => {
    const params = new URLSearchParams({
      q: query,
      type,
      limit: String(limit),
    });
    const response = await apiClient.get(`/search?${params.toString()}`);
    return response.data;
  },

  // Get search suggestions
  getSuggestions: async (query: string): Promise<{ success: boolean; data: string[] }> => {
    const response = await apiClient.get(`/search/suggestions?q=${encodeURIComponent(query)}`);
    return response.data;
  },

  // Get recent items
  getRecentItems: async (): Promise<{ success: boolean; data: SearchResult[] }> => {
    const response = await apiClient.get('/search/recent');
    return response.data;
  },
};

export default searchApi;
