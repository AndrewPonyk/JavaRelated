import React, { useState, useEffect, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardContent } from '../ui/Card';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import {
  Loader2, AlertCircle, Settings, RefreshCw, TrendingUp, TrendingDown,
  ArrowUpRight, ArrowDownRight, Minus, ChevronUp, ChevronDown, Search,
  ChevronLeft, ChevronRight, Filter
} from 'lucide-react';
import { useToastHelpers } from '../ui/Toaster';
import { cn } from '@/utils/cn';

// Types
interface WidgetData {
  id: string;
  title: string;
  type: 'chart' | 'table' | 'metric';
  data: any[];
  config: WidgetConfig;
  lastUpdated: string;
}

interface WidgetConfig {
  refreshInterval?: number;
  chartType?: 'line' | 'bar' | 'pie' | 'area';
  showLegend?: boolean;
  colorScheme?: string;
  prefix?: string;
  suffix?: string;
  precision?: number;
  showTrend?: boolean;
  showSparkline?: boolean;
  sortable?: boolean;
  filterable?: boolean;
  pageSize?: number;
  colors?: string[];
}

interface MetricData {
  value: number;
  label?: string;
  previousValue?: number;
  changePercent?: number;
  trend?: 'up' | 'down' | 'neutral';
  target?: number;
  sparklineData?: number[];
}

interface ChartDataPoint {
  label: string;
  value: number;
  category?: string;
  color?: string;
}

interface TableColumn {
  key: string;
  title: string;
  sortable?: boolean;
  type?: 'string' | 'number' | 'date' | 'currency';
}

interface DashboardWidgetProps {
  widgetId: string;
  onEdit?: (widgetId: string) => void;
  onDelete?: (widgetId: string) => void;
  className?: string;
}

// Custom hook for widget data fetching
const useWidgetData = (widgetId: string) => {
  return useQuery({
    queryKey: ['widget', widgetId],
    queryFn: async (): Promise<WidgetData> => {
      const response = await fetch(`/api/widgets/${widgetId}`);
      if (!response.ok) {
        throw new Error('Failed to fetch widget data');
      }
      return response.json();
    },
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    refetchOnWindowFocus: false,
  });
};

// Widget refresh mutation
const useRefreshWidget = (widgetId: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (): Promise<WidgetData> => {
      const response = await fetch(`/api/widgets/${widgetId}/refresh`, {
        method: 'POST',
      });
      if (!response.ok) {
        throw new Error('Failed to refresh widget');
      }
      return response.json();
    },
    onSuccess: (data) => {
      queryClient.setQueryData(['widget', widgetId], data);
    },
  });
};

// Sparkline component for metric trends
const Sparkline: React.FC<{ data: number[]; color?: string; height?: number }> = ({
  data,
  color = '#3b82f6',
  height = 40,
}) => {
  if (!data || data.length < 2) return null;

  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const width = 100;
  const padding = 2;

  const points = data.map((value, index) => {
    const x = padding + (index / (data.length - 1)) * (width - 2 * padding);
    const y = height - padding - ((value - min) / range) * (height - 2 * padding);
    return `${x},${y}`;
  }).join(' ');

  return (
    <svg width={width} height={height} className="overflow-visible">
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Endpoint dot */}
      <circle
        cx={padding + ((data.length - 1) / (data.length - 1)) * (width - 2 * padding)}
        cy={height - padding - ((data[data.length - 1] - min) / range) * (height - 2 * padding)}
        r={3}
        fill={color}
      />
    </svg>
  );
};

// Simple bar chart component
const SimpleBarChart: React.FC<{ data: ChartDataPoint[]; config: WidgetConfig }> = ({ data, config }) => {
  if (!data || data.length === 0) return null;

  const maxValue = Math.max(...data.map(d => d.value));
  const colors = config.colors || ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'];

  return (
    <div className="space-y-2">
      {data.slice(0, 8).map((item, index) => (
        <div key={index} className="flex items-center gap-3">
          <div className="w-24 text-sm text-gray-600 truncate" title={item.label}>
            {item.label}
          </div>
          <div className="flex-1 h-6 bg-gray-100 rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-500"
              style={{
                width: `${(item.value / maxValue) * 100}%`,
                backgroundColor: item.color || colors[index % colors.length],
              }}
            />
          </div>
          <div className="w-16 text-sm font-medium text-right">
            {config.prefix || ''}{item.value.toLocaleString()}{config.suffix || ''}
          </div>
        </div>
      ))}
    </div>
  );
};

// Simple pie chart component
const SimplePieChart: React.FC<{ data: ChartDataPoint[]; config: WidgetConfig }> = ({ data, config }) => {
  if (!data || data.length === 0) return null;

  const total = data.reduce((sum, item) => sum + item.value, 0);
  const colors = config.colors || ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];
  let currentAngle = 0;

  const segments = data.map((item, index) => {
    const angle = (item.value / total) * 360;
    const startAngle = currentAngle;
    currentAngle += angle;
    const endAngle = currentAngle;

    const startRadians = ((startAngle - 90) * Math.PI) / 180;
    const endRadians = ((endAngle - 90) * Math.PI) / 180;

    const x1 = 50 + 45 * Math.cos(startRadians);
    const y1 = 50 + 45 * Math.sin(startRadians);
    const x2 = 50 + 45 * Math.cos(endRadians);
    const y2 = 50 + 45 * Math.sin(endRadians);

    const largeArcFlag = angle > 180 ? 1 : 0;

    return {
      path: `M 50 50 L ${x1} ${y1} A 45 45 0 ${largeArcFlag} 1 ${x2} ${y2} Z`,
      color: item.color || colors[index % colors.length],
      label: item.label,
      value: item.value,
      percentage: ((item.value / total) * 100).toFixed(1),
    };
  });

  return (
    <div className="flex items-center gap-4">
      <svg viewBox="0 0 100 100" className="w-32 h-32">
        {segments.map((segment, index) => (
          <path
            key={index}
            d={segment.path}
            fill={segment.color}
            className="hover:opacity-80 transition-opacity cursor-pointer"
          />
        ))}
      </svg>
      {config.showLegend !== false && (
        <div className="flex-1 space-y-1">
          {segments.slice(0, 5).map((segment, index) => (
            <div key={index} className="flex items-center gap-2 text-sm">
              <div
                className="w-3 h-3 rounded-full"
                style={{ backgroundColor: segment.color }}
              />
              <span className="truncate flex-1 text-gray-600">{segment.label}</span>
              <span className="font-medium">{segment.percentage}%</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// Simple line chart component
const SimpleLineChart: React.FC<{ data: ChartDataPoint[]; config: WidgetConfig }> = ({ data, config }) => {
  if (!data || data.length === 0) return null;

  const values = data.map(d => d.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;

  const height = 150;
  const width = 100;
  const padding = { top: 10, bottom: 30, left: 10, right: 10 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;

  const points = data.map((item, index) => {
    const x = padding.left + (index / (data.length - 1)) * chartWidth;
    const y = padding.top + chartHeight - ((item.value - min) / range) * chartHeight;
    return { x, y, ...item };
  });

  const pathData = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
  const areaPathData = `${pathData} L ${points[points.length - 1].x} ${height - padding.bottom} L ${padding.left} ${height - padding.bottom} Z`;

  const color = config.colors?.[0] || '#3b82f6';

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-40" preserveAspectRatio="none">
      {/* Area fill */}
      <path d={areaPathData} fill={color} fillOpacity="0.1" />
      {/* Line */}
      <path d={pathData} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" />
      {/* Points */}
      {points.map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r="3" fill={color} className="hover:r-[5px] transition-all" />
      ))}
      {/* X-axis labels */}
      {data.length <= 8 && data.map((item, index) => (
        <text
          key={index}
          x={padding.left + (index / (data.length - 1)) * chartWidth}
          y={height - 5}
          textAnchor="middle"
          className="text-[8px] fill-gray-500"
        >
          {item.label.length > 6 ? item.label.slice(0, 6) : item.label}
        </text>
      ))}
    </svg>
  );
};

// Main component
const DashboardWidget: React.FC<DashboardWidgetProps> = ({
  widgetId,
  onEdit,
  onDelete,
  className,
}) => {
  const [isHovered, setIsHovered] = useState(false);
  const { data: widget, isLoading, error, refetch } = useWidgetData(widgetId);
  const refreshMutation = useRefreshWidget(widgetId);
  const { error: showError } = useToastHelpers();

  // Auto-refresh based on widget configuration
  useEffect(() => {
    if (!widget?.config.refreshInterval) return;

    const interval = setInterval(() => {
      refetch();
    }, widget.config.refreshInterval * 1000);

    return () => clearInterval(interval);
  }, [widget?.config.refreshInterval, refetch]);

  // Handle manual refresh
  const handleRefresh = async () => {
    try {
      await refreshMutation.mutateAsync();
    } catch (err) {
      console.error('Failed to refresh widget:', err);
      showError('Failed to refresh widget data');
    }
  };

  // Loading state
  if (isLoading) {
    return (
      <Card className={cn('min-h-[300px]', className)}>
        <CardContent className="flex items-center justify-center h-full">
          <div className="flex flex-col items-center space-y-2">
            <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
            <p className="text-sm text-gray-500">Loading widget...</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  // Error state
  if (error) {
    return (
      <Card className={cn('min-h-[300px] border-red-200', className)}>
        <CardContent className="flex items-center justify-center h-full">
          <div className="flex flex-col items-center space-y-4">
            <AlertCircle className="h-12 w-12 text-red-500" />
            <div className="text-center">
              <h3 className="text-lg font-semibold text-red-700">
                Failed to Load Widget
              </h3>
              <p className="text-sm text-gray-600 mt-1">
                {error instanceof Error ? error.message : 'Unknown error occurred'}
              </p>
            </div>
            <Button
              onClick={() => refetch()}
              variant="outline"
              size="sm"
              className="mt-2"
            >
              <RefreshCw className="h-4 w-4 mr-2" />
              Retry
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!widget) return null;

  return (
    <Card
      className={cn('transition-all duration-200 hover:shadow-lg', className)}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-base font-medium">
          {widget.title}
        </CardTitle>

        {/* Widget Actions */}
        <div className={cn(
          'flex items-center space-x-1 transition-opacity duration-200',
          isHovered ? 'opacity-100' : 'opacity-0'
        )}>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRefresh}
            disabled={refreshMutation.isPending}
            className="h-8 w-8 p-0"
          >
            <RefreshCw className={cn(
              'h-4 w-4',
              refreshMutation.isPending && 'animate-spin'
            )} />
          </Button>

          {onEdit && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onEdit(widgetId)}
              className="h-8 w-8 p-0"
            >
              <Settings className="h-4 w-4" />
            </Button>
          )}
        </div>
      </CardHeader>

      <CardContent>
        {/* Widget Content Based on Type */}
        <div className="min-h-[200px]">
          {widget.type === 'metric' && (
            <MetricWidget data={widget.data} config={widget.config} />
          )}

          {widget.type === 'chart' && (
            <ChartWidget data={widget.data} config={widget.config} />
          )}

          {widget.type === 'table' && (
            <TableWidget data={widget.data} config={widget.config} />
          )}
        </div>

        {/* Last Updated Info */}
        <div className="mt-4 pt-2 border-t border-gray-100">
          <p className="text-xs text-gray-500">
            Last updated: {new Date(widget.lastUpdated).toLocaleString()}
          </p>
        </div>
      </CardContent>
    </Card>
  );
};

// MetricWidget with sparklines, trends, and formatting
const MetricWidget: React.FC<{ data: any[]; config: WidgetConfig }> = ({ data, config }) => {
  const metric: MetricData = data[0] || {};

  const formatValue = (value: number | undefined) => {
    if (value === undefined || value === null) return 'N/A';
    const precision = config.precision ?? 0;
    const formatted = value.toLocaleString(undefined, {
      minimumFractionDigits: precision,
      maximumFractionDigits: precision,
    });
    return `${config.prefix || ''}${formatted}${config.suffix || ''}`;
  };

  const getTrendIcon = () => {
    if (!metric.trend || metric.trend === 'neutral') {
      return <Minus className="h-5 w-5 text-gray-400" />;
    }
    if (metric.trend === 'up') {
      return <TrendingUp className="h-5 w-5 text-green-500" />;
    }
    return <TrendingDown className="h-5 w-5 text-red-500" />;
  };

  const getChangeColor = () => {
    if (!metric.changePercent) return 'text-gray-500';
    if (metric.changePercent > 0) return 'text-green-600';
    if (metric.changePercent < 0) return 'text-red-600';
    return 'text-gray-500';
  };

  const getChangeIcon = () => {
    if (!metric.changePercent) return null;
    if (metric.changePercent > 0) return <ArrowUpRight className="h-4 w-4" />;
    if (metric.changePercent < 0) return <ArrowDownRight className="h-4 w-4" />;
    return null;
  };

  // Progress toward target
  const progressPercent = metric.target
    ? Math.min(100, (metric.value / metric.target) * 100)
    : null;

  return (
    <div className="flex flex-col items-center justify-center h-full space-y-4">
      <div className="text-center">
        <div className="text-4xl font-bold text-gray-900">
          {formatValue(metric.value)}
        </div>
        <div className="text-sm text-gray-500 mt-1">
          {metric.label || 'Metric'}
        </div>
      </div>

      {/* Trend indicator */}
      {config.showTrend !== false && metric.changePercent !== undefined && (
        <div className={cn('flex items-center gap-1', getChangeColor())}>
          {getChangeIcon()}
          <span className="text-sm font-medium">
            {metric.changePercent > 0 ? '+' : ''}{metric.changePercent.toFixed(1)}%
          </span>
          <span className="text-xs text-gray-400 ml-1">vs previous</span>
        </div>
      )}

      {/* Sparkline */}
      {config.showSparkline !== false && metric.sparklineData && metric.sparklineData.length > 1 && (
        <div className="w-24">
          <Sparkline
            data={metric.sparklineData}
            color={metric.trend === 'up' ? '#22c55e' : metric.trend === 'down' ? '#ef4444' : '#6b7280'}
            height={30}
          />
        </div>
      )}

      {/* Progress bar toward target */}
      {progressPercent !== null && (
        <div className="w-full max-w-xs">
          <div className="flex justify-between text-xs text-gray-500 mb-1">
            <span>Progress</span>
            <span>{progressPercent.toFixed(0)}% of target</span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className={cn(
                'h-2 rounded-full transition-all',
                progressPercent >= 100 ? 'bg-green-500' :
                progressPercent >= 75 ? 'bg-blue-500' :
                progressPercent >= 50 ? 'bg-yellow-500' : 'bg-red-500'
              )}
              style={{ width: `${Math.min(100, progressPercent)}%` }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

// ChartWidget with multiple chart types
const ChartWidget: React.FC<{ data: any[]; config: WidgetConfig }> = ({ data, config }) => {
  const chartType = config.chartType || 'line';

  if (!data || data.length === 0) {
    return (
      <div className="flex items-center justify-center h-full bg-gray-50 rounded">
        <p className="text-gray-400">No data available</p>
      </div>
    );
  }

  switch (chartType) {
    case 'bar':
      return <SimpleBarChart data={data} config={config} />;
    case 'pie':
      return <SimplePieChart data={data} config={config} />;
    case 'line':
    case 'area':
    default:
      return <SimpleLineChart data={data} config={config} />;
  }
};

// TableWidget with sorting, filtering, and pagination
const TableWidget: React.FC<{ data: any[]; config: WidgetConfig }> = ({ data, config }) => {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [filter, setFilter] = useState('');
  const [currentPage, setCurrentPage] = useState(1);

  const pageSize = config.pageSize || 5;

  // Extract columns from data
  const columns: TableColumn[] = useMemo(() => {
    if (!data || data.length === 0) return [];
    const firstRow = data[0];
    return Object.keys(firstRow).map(key => ({
      key,
      title: key.charAt(0).toUpperCase() + key.slice(1).replace(/([A-Z])/g, ' $1'),
      sortable: config.sortable !== false,
    }));
  }, [data, config.sortable]);

  // Filter data
  const filteredData = useMemo(() => {
    if (!filter || config.filterable === false) return data;
    const lowerFilter = filter.toLowerCase();
    return data.filter(row =>
      Object.values(row).some(value =>
        String(value).toLowerCase().includes(lowerFilter)
      )
    );
  }, [data, filter, config.filterable]);

  // Sort data
  const sortedData = useMemo(() => {
    if (!sortKey) return filteredData;
    return [...filteredData].sort((a, b) => {
      const aVal = a[sortKey];
      const bVal = b[sortKey];
      if (aVal === bVal) return 0;
      if (sortDirection === 'asc') {
        return aVal < bVal ? -1 : 1;
      }
      return aVal > bVal ? -1 : 1;
    });
  }, [filteredData, sortKey, sortDirection]);

  // Paginate data
  const paginatedData = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return sortedData.slice(start, start + pageSize);
  }, [sortedData, currentPage, pageSize]);

  const totalPages = Math.ceil(sortedData.length / pageSize);

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDirection(prev => prev === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDirection('asc');
    }
    setCurrentPage(1);
  };

  const formatCellValue = (value: any) => {
    if (value === null || value === undefined) return '-';
    if (typeof value === 'number') {
      return value.toLocaleString();
    }
    return String(value);
  };

  if (!data || data.length === 0) {
    return (
      <div className="flex items-center justify-center h-full bg-gray-50 rounded">
        <p className="text-gray-400">No data available</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {/* Filter input */}
      {config.filterable !== false && (
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <Input
            value={filter}
            onChange={(e) => {
              setFilter(e.target.value);
              setCurrentPage(1);
            }}
            placeholder="Filter data..."
            className="pl-9 h-8 text-sm"
          />
        </div>
      )}

      {/* Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-gray-50">
              {columns.map(col => (
                <th
                  key={col.key}
                  className={cn(
                    'text-left py-2 px-3 font-medium text-gray-600',
                    col.sortable && 'cursor-pointer hover:bg-gray-100'
                  )}
                  onClick={() => col.sortable && handleSort(col.key)}
                >
                  <div className="flex items-center gap-1">
                    {col.title}
                    {sortKey === col.key && (
                      sortDirection === 'asc'
                        ? <ChevronUp className="h-4 w-4" />
                        : <ChevronDown className="h-4 w-4" />
                    )}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {paginatedData.map((row, rowIndex) => (
              <tr key={rowIndex} className="border-b hover:bg-gray-50">
                {columns.map(col => (
                  <td key={col.key} className="py-2 px-3">
                    {formatCellValue(row[col.key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-gray-500">
            Showing {(currentPage - 1) * pageSize + 1}-{Math.min(currentPage * pageSize, sortedData.length)} of {sortedData.length}
          </span>
          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              className="h-8 w-8 p-0"
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="px-2">
              {currentPage} / {totalPages}
            </span>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
              className="h-8 w-8 p-0"
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};

export default DashboardWidget;
export type { DashboardWidgetProps, WidgetData, WidgetConfig };
