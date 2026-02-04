import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Search, FileText, BarChart3, Users, Settings, Calendar,
  LayoutDashboard, Table, User, HelpCircle, Database, Bell,
  Shield, FileSearch, Loader2, LayoutGrid, Hash, PieChart,
  LineChart, Map, Grid, Lightbulb, Brain, DollarSign, Package,
  Share2
} from 'lucide-react';
import { cn } from '@/utils/cn';
import { searchApi, SearchResult as ApiSearchResult, SearchType } from '@/services/api/searchApi';
import { queryKeys } from '@/services/cache/queryClient';

interface SearchResult {
  id: string;
  title: string;
  description?: string;
  type: 'dashboard' | 'widget' | 'user' | 'page' | 'action';
  icon?: React.ReactNode;
  url?: string;
  action?: () => void;
}

interface SearchCommandProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

// Icon mapping for search results
const iconMap: Record<string, React.ElementType> = {
  LayoutDashboard,
  LayoutGrid,
  Table,
  User,
  Users,
  Settings,
  FileText,
  Database,
  Bell,
  Shield,
  FileSearch,
  Calendar,
  HelpCircle,
  Hash,
  PieChart,
  BarChart3,
  LineChart,
  Map,
  Grid,
  Lightbulb,
  Brain,
  DollarSign,
  Package,
  Share2,
};

const getIconComponent = (iconName?: string): React.ReactNode => {
  if (!iconName) return <FileText className="h-4 w-4" />;
  const IconComponent = iconMap[iconName] || FileText;
  return <IconComponent className="h-4 w-4" />;
};

// Convert API result to component result
const convertApiResult = (result: ApiSearchResult): SearchResult => ({
  id: result.id,
  title: result.title,
  description: result.description,
  type: result.type,
  icon: getIconComponent(result.icon),
  url: result.url,
});

// Debounce hook
function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}

export const SearchCommand: React.FC<SearchCommandProps> = ({ open, onOpenChange }) => {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [searchType, setSearchType] = useState<SearchType>('all');
  const navigate = useNavigate();

  // Debounce search query
  const debouncedQuery = useDebounce(query, 300);

  // Search query
  const { data, isLoading, isError } = useQuery({
    queryKey: queryKeys.search.results(debouncedQuery, searchType),
    queryFn: () => searchApi.search(debouncedQuery, searchType, 15),
    enabled: open && debouncedQuery.length >= 1,
    staleTime: 1 * 60 * 1000, // 1 minute
  });

  // Recent items query (when no search query)
  const { data: recentData } = useQuery({
    queryKey: queryKeys.search.recent(),
    queryFn: () => searchApi.getRecentItems(),
    enabled: open && debouncedQuery.length === 0,
    staleTime: 5 * 60 * 1000,
  });

  // Convert and use results
  const searchResults: SearchResult[] = useMemo(() => {
    if (debouncedQuery.length > 0 && data?.data) {
      return data.data.map(convertApiResult);
    }
    if (debouncedQuery.length === 0 && recentData?.data) {
      return recentData.data.map(convertApiResult);
    }
    return [];
  }, [data, recentData, debouncedQuery]);

  // Handle keyboard navigation
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (!open) return;

      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex((prev) =>
            prev < searchResults.length - 1 ? prev + 1 : prev
          );
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex((prev) => (prev > 0 ? prev - 1 : prev));
          break;
        case 'Enter':
          e.preventDefault();
          if (searchResults[selectedIndex]) {
            handleSelectResult(searchResults[selectedIndex]);
          }
          break;
        case 'Escape':
          e.preventDefault();
          onOpenChange(false);
          break;
      }
    },
    [open, searchResults, selectedIndex, onOpenChange]
  );

  // Handle result selection
  const handleSelectResult = useCallback(
    (result: SearchResult) => {
      if (result.action) {
        result.action();
      } else if (result.url) {
        navigate(result.url);
      }
      onOpenChange(false);
      setQuery('');
      setSelectedIndex(0);
    },
    [navigate, onOpenChange]
  );

  // Global keyboard shortcut (Cmd/Ctrl + K)
  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        onOpenChange(true);
      }
    };

    document.addEventListener('keydown', handleGlobalKeyDown);
    return () => document.removeEventListener('keydown', handleGlobalKeyDown);
  }, [onOpenChange]);

  // Local keyboard navigation
  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setQuery('');
      setSelectedIndex(0);
      setSearchType('all');
    }
  }, [open]);

  // Reset selected index when results change
  useEffect(() => {
    setSelectedIndex(0);
  }, [searchResults.length]);

  if (!open) return null;

  const typeFilters: { value: SearchType; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: 'dashboards', label: 'Dashboards' },
    { value: 'widgets', label: 'Widgets' },
    { value: 'pages', label: 'Pages' },
    { value: 'users', label: 'Users' },
  ];

  return (
    <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm" onClick={() => onOpenChange(false)}>
      <div
        className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="bg-white rounded-lg shadow-2xl border border-gray-200 overflow-hidden">
          {/* Search Input */}
          <div className="flex items-center border-b border-gray-200 px-4">
            {isLoading ? (
              <Loader2 className="h-5 w-5 text-gray-400 shrink-0 animate-spin" />
            ) : (
              <Search className="h-5 w-5 text-gray-400 shrink-0" />
            )}
            <input
              type="text"
              placeholder="Search dashboards, widgets, pages..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="flex-1 px-3 py-4 text-sm bg-transparent border-none outline-none placeholder:text-gray-400"
              autoFocus
            />
            <kbd className="hidden sm:inline-flex h-5 select-none items-center gap-1 rounded border bg-gray-100 px-1.5 font-mono text-[10px] font-medium text-gray-500">
              ESC
            </kbd>
          </div>

          {/* Type Filters */}
          <div className="flex items-center gap-1 px-4 py-2 border-b border-gray-100 bg-gray-50">
            {typeFilters.map((filter) => (
              <button
                key={filter.value}
                onClick={() => setSearchType(filter.value)}
                className={cn(
                  'px-2 py-1 rounded text-xs font-medium transition-colors',
                  searchType === filter.value
                    ? 'bg-blue-100 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-100'
                )}
              >
                {filter.label}
              </button>
            ))}
          </div>

          {/* Results */}
          <div className="max-h-96 overflow-y-auto">
            {isLoading ? (
              <div className="px-4 py-8 text-center">
                <Loader2 className="h-8 w-8 mx-auto mb-2 text-blue-500 animate-spin" />
                <p className="text-sm text-gray-500">Searching...</p>
              </div>
            ) : isError ? (
              <div className="px-4 py-8 text-center text-sm text-red-500">
                Failed to search. Please try again.
              </div>
            ) : searchResults.length > 0 ? (
              <div className="py-2">
                {debouncedQuery.length === 0 && (
                  <div className="px-4 py-2 text-xs font-medium text-gray-400 uppercase">
                    Recent
                  </div>
                )}
                {searchResults.map((result, index) => (
                  <button
                    key={result.id}
                    className={cn(
                      'w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-gray-50 transition-colors',
                      index === selectedIndex && 'bg-blue-50 border-r-2 border-blue-500'
                    )}
                    onClick={() => handleSelectResult(result)}
                    onMouseEnter={() => setSelectedIndex(index)}
                  >
                    <div className="flex items-center justify-center w-8 h-8 rounded-md bg-gray-100 text-gray-600">
                      {result.icon || <FileText className="h-4 w-4" />}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-900 truncate">
                        {result.title}
                      </div>
                      {result.description && (
                        <div className="text-xs text-gray-500 truncate">
                          {result.description}
                        </div>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded-md capitalize">
                        {result.type}
                      </span>
                      {index === selectedIndex && (
                        <kbd className="hidden sm:inline-flex h-5 select-none items-center gap-1 rounded border bg-gray-100 px-1.5 font-mono text-[10px] font-medium text-gray-600">
                          ⏎
                        </kbd>
                      )}
                    </div>
                  </button>
                ))}
              </div>
            ) : query ? (
              <div className="px-4 py-8 text-center text-sm text-gray-500">
                <Search className="h-8 w-8 mx-auto mb-2 text-gray-300" />
                No results found for "{query}"
              </div>
            ) : (
              <div className="px-4 py-8 text-center text-sm text-gray-500">
                <Search className="h-8 w-8 mx-auto mb-2 text-gray-300" />
                Start typing to search...
              </div>
            )}
          </div>

          {/* Footer */}
          {searchResults.length > 0 && (
            <div className="border-t border-gray-200 px-4 py-3 text-xs text-gray-500 bg-gray-50">
              <div className="flex items-center justify-between">
                <span>
                  {searchResults.length} result{searchResults.length !== 1 ? 's' : ''}
                </span>
                <div className="flex items-center gap-4">
                  <span className="flex items-center gap-1">
                    <kbd className="inline-flex h-4 select-none items-center gap-1 rounded border bg-white px-1 font-mono text-[10px] font-medium">
                      ↑↓
                    </kbd>
                    to navigate
                  </span>
                  <span className="flex items-center gap-1">
                    <kbd className="inline-flex h-4 select-none items-center gap-1 rounded border bg-white px-1 font-mono text-[10px] font-medium">
                      ⏎
                    </kbd>
                    to select
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SearchCommand;
