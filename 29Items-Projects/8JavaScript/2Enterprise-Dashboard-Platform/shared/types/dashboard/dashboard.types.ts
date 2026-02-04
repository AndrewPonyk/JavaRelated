// Shared TypeScript type definitions for Dashboard Platform
// These types are used across frontend, backend, and shared modules

/**
 * Core Dashboard Types
 */
export interface Dashboard {
  id: string;
  title: string;
  description?: string;
  slug?: string;
  layout: WidgetLayout[];
  settings: DashboardSettings;
  isPublic: boolean;
  isTemplate: boolean;
  userId: string;
  createdAt: string;
  updatedAt: string;

  // Relations (optional - loaded when needed)
  user?: User;
  widgets?: Widget[];
  shares?: DashboardShare[];
  analytics?: DashboardAnalytics[];
}

export interface DashboardSettings {
  theme?: 'light' | 'dark' | 'auto';
  refreshInterval?: number; // in seconds
  allowExport?: boolean;
  allowShare?: boolean;
  showGrid?: boolean;
  gridSize?: number;
  backgroundColor?: string;
  fontFamily?: string;
  maxColumns?: number;
  compactMode?: boolean;
  animations?: boolean;
}

export interface WidgetLayout {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  minWidth?: number;
  minHeight?: number;
  maxWidth?: number;
  maxHeight?: number;
  isDraggable?: boolean;
  isResizable?: boolean;
  static?: boolean;
}

/**
 * Widget Types
 */
export interface Widget {
  id: string;
  title: string;
  description?: string;
  type: WidgetType;
  config: WidgetConfig;
  position: WidgetPosition;
  query?: string;
  dataSource?: string;
  refreshRate?: number; // in seconds
  dashboardId: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
  cachedData?: any;
  lastFetch?: string;

  // Relations
  dashboard?: Dashboard;
  user?: User;
}

export type WidgetType =
  | 'CHART_LINE'
  | 'CHART_BAR'
  | 'CHART_PIE'
  | 'CHART_AREA'
  | 'CHART_SCATTER'
  | 'TABLE'
  | 'METRIC'
  | 'TEXT'
  | 'IMAGE'
  | 'MAP'
  | 'HEATMAP'
  | 'GAUGE'
  | 'CUSTOM';

export interface WidgetConfig {
  // Chart-specific config
  chartType?: 'line' | 'bar' | 'pie' | 'area' | 'scatter' | 'doughnut';
  showLegend?: boolean;
  showGrid?: boolean;
  showTooltip?: boolean;
  colorScheme?: string[];
  animation?: boolean;

  // Data configuration
  xAxis?: string;
  yAxis?: string[];
  groupBy?: string;
  aggregation?: 'sum' | 'avg' | 'count' | 'min' | 'max';

  // Display options
  showTitle?: boolean;
  showBorder?: boolean;
  backgroundColor?: string;
  textColor?: string;
  fontSize?: number;

  // Table-specific config
  sortable?: boolean;
  filterable?: boolean;
  pagination?: boolean;
  rowsPerPage?: number;

  // Metric-specific config
  format?: 'number' | 'currency' | 'percentage' | 'bytes';
  precision?: number;
  prefix?: string;
  suffix?: string;

  // Custom properties
  [key: string]: any;
}

export interface WidgetPosition {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * User Types
 */
export interface User {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role: UserRole;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  lastLogin?: string;

  // Relations
  profile?: UserProfile;
}

export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'MANAGER' | 'USER' | 'VIEWER';

export interface UserProfile {
  id: string;
  userId: string;
  theme: 'light' | 'dark' | 'auto';
  timezone: string;
  language: string;
  notifications: NotificationSettings;
  avatar?: string;
  bio?: string;
}

export interface NotificationSettings {
  email?: boolean;
  push?: boolean;
  dashboard?: boolean;
  security?: boolean;
  updates?: boolean;
}

/**
 * Dashboard Sharing Types
 */
export interface DashboardShare {
  id: string;
  permission: SharePermission;
  dashboardId: string;
  userId: string;
  sharedBy: string;
  sharedAt: string;
  expiresAt?: string;

  // Relations
  dashboard?: Dashboard;
  user?: User;
}

export type SharePermission = 'READ' | 'WRITE' | 'ADMIN';

/**
 * Analytics Types
 */
export interface DashboardAnalytics {
  id: string;
  views: number;
  uniqueViews: number;
  avgLoadTime?: number;
  bounceRate?: number;
  date: string;
  granularity: 'hour' | 'day' | 'week' | 'month';
  dashboardId: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Data Connection Types
 */
export interface DataConnection {
  id: string;
  name: string;
  type: DataConnectionType;
  config: DataConnectionConfig;
  credentials: Record<string, any>; // Encrypted
  isActive: boolean;
  lastTested?: string;
  status: ConnectionStatus;
  userId: string;
  createdAt: string;
  updatedAt: string;
}

export type DataConnectionType =
  | 'MYSQL'
  | 'POSTGRESQL'
  | 'MONGODB'
  | 'REST_API'
  | 'GRAPHQL_API'
  | 'CSV_FILE'
  | 'JSON_FILE'
  | 'GOOGLE_SHEETS'
  | 'SALESFORCE'
  | 'HUBSPOT'
  | 'SLACK'
  | 'CUSTOM';

export type ConnectionStatus = 'PENDING' | 'CONNECTED' | 'FAILED' | 'DISABLED';

export interface DataConnectionConfig {
  host?: string;
  port?: number;
  database?: string;
  schema?: string;
  url?: string;
  apiVersion?: string;
  timeout?: number;
  retries?: number;
  [key: string]: any;
}

/**
 * API Response Types
 */
export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  errors?: ApiError[];
  meta?: ApiMeta;
}

export interface ApiError {
  code: string;
  message: string;
  field?: string;
  details?: any;
}

export interface ApiMeta {
  total?: number;
  page?: number;
  limit?: number;
  pages?: number;
  hasNext?: boolean;
  hasPrev?: boolean;
}

export interface PaginatedResponse<T> extends ApiResponse<T[]> {
  meta: Required<ApiMeta>;
}

/**
 * Query and Filter Types
 */
export interface DashboardQuery {
  page?: number;
  limit?: number;
  search?: string;
  sortBy?: 'createdAt' | 'updatedAt' | 'title';
  sortOrder?: 'asc' | 'desc';
  isPublic?: boolean;
  isTemplate?: boolean;
  userId?: string;
  tags?: string[];
}

export interface WidgetQuery {
  dashboardId?: string;
  type?: WidgetType[];
  search?: string;
  page?: number;
  limit?: number;
}

/**
 * Event Types for Real-time Updates
 */
export interface WebSocketEvent<T = any> {
  type: string;
  payload: T;
  timestamp: string;
  userId?: string;
  dashboardId?: string;
}

export interface DashboardUpdateEvent {
  type: 'dashboard:update' | 'dashboard:delete';
  dashboardId: string;
  userId: string;
  changes?: Partial<Dashboard>;
}

export interface WidgetUpdateEvent {
  type: 'widget:update' | 'widget:delete' | 'widget:data';
  widgetId: string;
  dashboardId: string;
  userId: string;
  data?: any;
  changes?: Partial<Widget>;
}

/**
 * Machine Learning Types
 */
export interface MLPrediction {
  id: string;
  input: any;
  output: any;
  confidence?: number;
  modelId: string;
  createdAt: string;
}

export interface AnomalyDetection {
  timestamp: string;
  value: number;
  predicted: number;
  anomalyScore: number;
  isAnomaly: boolean;
  severity: 'low' | 'medium' | 'high' | 'critical';
  explanation?: string;
}

/**
 * Export Types
 */
export interface ExportOptions {
  format: 'pdf' | 'csv' | 'xlsx' | 'json' | 'png';
  includeData?: boolean;
  includeLayout?: boolean;
  dateRange?: {
    start: string;
    end: string;
  };
  filters?: Record<string, any>;
}

/**
 * Utility Types
 */
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

export type RequiredFields<T, K extends keyof T> = T & Required<Pick<T, K>>;

export type OptionalFields<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>;

// Re-export commonly used types for convenience
export type {
  Dashboard as DashboardType,
  Widget as WidgetType,
  User as UserType,
  DashboardShare as ShareType,
};