export interface Widget {
  id: string;
  type: WidgetType;
  title: string;
  description?: string;
  config: WidgetConfig;
  layout: WidgetLayout;
  data?: any;
  lastUpdated?: string;
  refreshInterval?: number; // in seconds
  dataSource?: DataSource;
}

export interface WidgetLayout {
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number;
  minH?: number;
  maxW?: number;
  maxH?: number;
  static?: boolean;
  resizable?: boolean;
  draggable?: boolean;
}

export interface WidgetConfig {
  [key: string]: any;
  // Chart-specific configs
  chartType?: ChartType;
  colors?: string[];
  showLegend?: boolean;
  showGrid?: boolean;
  xAxisLabel?: string;
  yAxisLabel?: string;

  // Metric-specific configs
  unit?: string;
  prefix?: string;
  suffix?: string;
  precision?: number;
  threshold?: {
    warning?: number;
    critical?: number;
  };

  // Table-specific configs
  columns?: TableColumn[];
  sortable?: boolean;
  filterable?: boolean;
  pagination?: boolean;
  pageSize?: number;

  // General configs
  backgroundColor?: string;
  textColor?: string;
  fontSize?: 'sm' | 'md' | 'lg';
  alignment?: 'left' | 'center' | 'right';
}

export interface TableColumn {
  key: string;
  title: string;
  width?: number;
  sortable?: boolean;
  filterable?: boolean;
  formatter?: (value: any) => string;
}

export interface DataSource {
  id: string;
  name: string;
  type: DataSourceType;
  endpoint?: string;
  query?: string;
  parameters?: Record<string, any>;
  headers?: Record<string, string>;
  refreshInterval?: number;
}

export type DataSourceType =
  | 'api'
  | 'database'
  | 'file'
  | 'websocket'
  | 'static';

export type WidgetType =
  | 'metric'
  | 'chart'
  | 'table'
  | 'text'
  | 'image'
  | 'iframe'
  | 'calendar'
  | 'map'
  | 'progress'
  | 'gauge'
  | 'list'
  | 'anomaly_detection'
  | 'ai_insights'
  | 'forecast';

export type ChartType =
  | 'line'
  | 'bar'
  | 'area'
  | 'pie'
  | 'doughnut'
  | 'scatter'
  | 'bubble'
  | 'radar'
  | 'polar'
  | 'funnel'
  | 'treemap'
  | 'heatmap';

export interface Dashboard {
  id: string;
  title: string;
  description?: string;
  widgets: Widget[];
  layout: DashboardLayout;
  settings: DashboardSettings;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  isPublic?: boolean;
  sharedWith?: string[];
  tags?: string[];
  thumbnail?: string;
}

export interface DashboardLayout {
  cols: number;
  rowHeight: number;
  margin: [number, number];
  padding: [number, number];
  breakpoints: {
    lg: number;
    md: number;
    sm: number;
    xs: number;
    xxs: number;
  };
  layouts: {
    [breakpoint: string]: WidgetLayout[];
  };
}

export interface DashboardSettings {
  theme?: 'light' | 'dark' | 'auto';
  autoRefresh?: boolean;
  refreshInterval?: number;
  showTitle?: boolean;
  showDescription?: boolean;
  allowEditing?: boolean;
  allowSharing?: boolean;
  backgroundColor?: string;
  textColor?: string;
  gridSize?: number;
  snapToGrid?: boolean;
  compactType?: 'vertical' | 'horizontal' | null;
}

export interface WidgetTemplate {
  id: string;
  name: string;
  description: string;
  type: WidgetType;
  category: WidgetCategory;
  thumbnail: string;
  defaultConfig: WidgetConfig;
  defaultLayout: Partial<WidgetLayout>;
  requiredDataFields?: string[];
  sampleData?: any;
}

export type WidgetCategory =
  | 'analytics'
  | 'metrics'
  | 'visualization'
  | 'content'
  | 'interactive'
  | 'external'
  | 'ml_analytics';

export interface DashboardFilters {
  search?: string;
  tags?: string[];
  owner?: string;
  dateRange?: {
    start: string;
    end: string;
  };
  sortBy?: 'name' | 'created' | 'updated';
  sortOrder?: 'asc' | 'desc';
}

export interface WidgetData {
  id: string;
  data: any;
  timestamp: string;
  error?: string;
  loading?: boolean;
}

// Event types for real-time updates
export interface DashboardEvent {
  type: DashboardEventType;
  payload: any;
  timestamp: string;
  userId?: string;
  dashboardId: string;
}

export type DashboardEventType =
  | 'widget_updated'
  | 'widget_added'
  | 'widget_removed'
  | 'widget_moved'
  | 'widget_resized'
  | 'dashboard_updated'
  | 'dashboard_shared'
  | 'data_updated';