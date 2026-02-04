import React from 'react';
import { BarChart3, LineChart, PieChart, Table, Hash, Type, Image, Globe, Calendar, MapPin, TrendingUp, Gauge, Brain, AlertTriangle, Target } from 'lucide-react';
import { WidgetType, WidgetTemplate, Widget, WidgetConfig, WidgetLayout } from '@/types/dashboard';

// Import widget components
import MetricWidget from './widgets/MetricWidget';
import ChartWidget from './widgets/ChartWidget';
import TableWidget from './widgets/TableWidget';
import AnomalyDetectionWidget from './widgets/AnomalyDetectionWidget';
import AIInsightsWidget from './widgets/AIInsightsWidget';
import ForecastWidget from './widgets/ForecastWidget';

// Widget component map
export const widgetComponents: Record<WidgetType, React.ComponentType<any>> = {
  metric: MetricWidget,
  chart: ChartWidget,
  table: TableWidget,
  anomaly_detection: AnomalyDetectionWidget,
  ai_insights: AIInsightsWidget,
  forecast: ForecastWidget,
  // Placeholders for other widget types
  text: ({ widget, data, ...props }: any) => (
    <div className="h-full flex items-center justify-center text-gray-400">
      <div className="text-center">
        <Type className="h-8 w-8 mx-auto mb-2" />
        <div className="text-sm">Text Widget</div>
        <div className="text-xs opacity-75">Coming Soon</div>
      </div>
    </div>
  ),
  image: ({ widget, data, ...props }: any) => (
    <div className="h-full flex items-center justify-center text-gray-400">
      <div className="text-center">
        <Image className="h-8 w-8 mx-auto mb-2" />
        <div className="text-sm">Image Widget</div>
        <div className="text-xs opacity-75">Coming Soon</div>
      </div>
    </div>
  ),
  iframe: ({ widget, data, ...props }: any) => (
    <div className="h-full flex items-center justify-center text-gray-400">
      <div className="text-center">
        <Globe className="h-8 w-8 mx-auto mb-2" />
        <div className="text-sm">IFrame Widget</div>
        <div className="text-xs opacity-75">Coming Soon</div>
      </div>
    </div>
  ),
  calendar: ({ widget, data, ...props }: any) => (
    <div className="h-full flex items-center justify-center text-gray-400">
      <div className="text-center">
        <Calendar className="h-8 w-8 mx-auto mb-2" />
        <div className="text-sm">Calendar Widget</div>
        <div className="text-xs opacity-75">Coming Soon</div>
      </div>
    </div>
  ),
  map: ({ widget, data, ...props }: any) => (
    <div className="h-full flex items-center justify-center text-gray-400">
      <div className="text-center">
        <MapPin className="h-8 w-8 mx-auto mb-2" />
        <div className="text-sm">Map Widget</div>
        <div className="text-xs opacity-75">Coming Soon</div>
      </div>
    </div>
  ),
  progress: ({ widget, data, ...props }: any) => (
    <div className="h-full flex items-center justify-center text-gray-400">
      <div className="text-center">
        <TrendingUp className="h-8 w-8 mx-auto mb-2" />
        <div className="text-sm">Progress Widget</div>
        <div className="text-xs opacity-75">Coming Soon</div>
      </div>
    </div>
  ),
  gauge: ({ widget, data, ...props }: any) => (
    <div className="h-full flex items-center justify-center text-gray-400">
      <div className="text-center">
        <Gauge className="h-8 w-8 mx-auto mb-2" />
        <div className="text-sm">Gauge Widget</div>
        <div className="text-xs opacity-75">Coming Soon</div>
      </div>
    </div>
  ),
  list: ({ widget, data, ...props }: any) => (
    <div className="h-full flex items-center justify-center text-gray-400">
      <div className="text-center">
        <Hash className="h-8 w-8 mx-auto mb-2" />
        <div className="text-sm">List Widget</div>
        <div className="text-xs opacity-75">Coming Soon</div>
      </div>
    </div>
  ),
};

// Widget templates for the widget gallery
export const widgetTemplates: WidgetTemplate[] = [
  // Metric widgets
  {
    id: 'metric-kpi',
    name: 'KPI Metric',
    description: 'Display a key performance indicator with trend',
    type: 'metric',
    category: 'metrics',
    thumbnail: '/images/widgets/metric-kpi.png',
    defaultConfig: {
      unit: '',
      precision: 0,
      showTrend: true,
      alignment: 'left',
    },
    defaultLayout: { x: 0, y: 0, w: 3, h: 2, minW: 2, minH: 2 },
    sampleData: {
      value: 12500,
      previousValue: 11200,
      change: 1300,
      changePercent: 11.6,
      trend: 'up',
    },
  },
  {
    id: 'metric-revenue',
    name: 'Revenue Metric',
    description: 'Display revenue with currency formatting',
    type: 'metric',
    category: 'metrics',
    thumbnail: '/images/widgets/metric-revenue.png',
    defaultConfig: {
      prefix: '$',
      precision: 0,
      unit: 'USD',
      alignment: 'left',
    },
    defaultLayout: { x: 0, y: 0, w: 3, h: 2, minW: 2, minH: 2 },
    sampleData: {
      value: 125000,
      previousValue: 112000,
      changePercent: 11.6,
      trend: 'up',
    },
  },

  // Chart widgets
  {
    id: 'chart-line',
    name: 'Line Chart',
    description: 'Display data trends over time',
    type: 'chart',
    category: 'visualization',
    thumbnail: '/images/widgets/chart-line.png',
    defaultConfig: {
      chartType: 'line',
      showGrid: true,
      showLegend: true,
      colors: ['#3b82f6', '#10b981'],
    },
    defaultLayout: { x: 0, y: 0, w: 6, h: 4, minW: 4, minH: 3 },
    sampleData: [
      { month: 'Jan', sales: 4000, profit: 2400 },
      { month: 'Feb', sales: 3000, profit: 1398 },
      { month: 'Mar', sales: 2000, profit: 9800 },
      { month: 'Apr', sales: 2780, profit: 3908 },
      { month: 'May', sales: 1890, profit: 4800 },
      { month: 'Jun', sales: 2390, profit: 3800 },
    ],
  },
  {
    id: 'chart-bar',
    name: 'Bar Chart',
    description: 'Compare different categories',
    type: 'chart',
    category: 'visualization',
    thumbnail: '/images/widgets/chart-bar.png',
    defaultConfig: {
      chartType: 'bar',
      showGrid: true,
      showLegend: true,
      colors: ['#3b82f6', '#10b981'],
    },
    defaultLayout: { x: 0, y: 0, w: 6, h: 4, minW: 4, minH: 3 },
    sampleData: [
      { category: 'Product A', sales: 4000, profit: 2400 },
      { category: 'Product B', sales: 3000, profit: 1398 },
      { category: 'Product C', sales: 2000, profit: 9800 },
      { category: 'Product D', sales: 2780, profit: 3908 },
    ],
  },
  {
    id: 'chart-pie',
    name: 'Pie Chart',
    description: 'Show data distribution',
    type: 'chart',
    category: 'visualization',
    thumbnail: '/images/widgets/chart-pie.png',
    defaultConfig: {
      chartType: 'pie',
      showLegend: true,
      colors: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444'],
    },
    defaultLayout: { x: 0, y: 0, w: 4, h: 4, minW: 3, minH: 3 },
    sampleData: [
      { name: 'Desktop', value: 400 },
      { name: 'Mobile', value: 300 },
      { name: 'Tablet', value: 200 },
      { name: 'Other', value: 100 },
    ],
  },

  // Table widgets
  {
    id: 'table-basic',
    name: 'Data Table',
    description: 'Display tabular data with sorting and filtering',
    type: 'table',
    category: 'content',
    thumbnail: '/images/widgets/table-basic.png',
    defaultConfig: {
      sortable: true,
      filterable: true,
      pagination: true,
      pageSize: 10,
    },
    defaultLayout: { x: 0, y: 0, w: 8, h: 6, minW: 6, minH: 4 },
    sampleData: {
      columns: [
        { key: 'name', title: 'Name', sortable: true },
        { key: 'email', title: 'Email', sortable: true },
        { key: 'status', title: 'Status', sortable: true },
        { key: 'lastLogin', title: 'Last Login', sortable: true },
      ],
      rows: [
        { name: 'John Doe', email: 'john@example.com', status: 'Active', lastLogin: '2024-01-15' },
        { name: 'Jane Smith', email: 'jane@example.com', status: 'Active', lastLogin: '2024-01-14' },
        { name: 'Bob Johnson', email: 'bob@example.com', status: 'Inactive', lastLogin: '2024-01-10' },
      ],
    },
  },

  // ML Analytics widgets
  {
    id: 'anomaly-detection',
    name: 'Anomaly Detection',
    description: 'AI-powered anomaly detection and alerting',
    type: 'anomaly_detection',
    category: 'ml_analytics',
    thumbnail: '/images/widgets/anomaly-detection.png',
    defaultConfig: {
      sensitivity: 'medium',
      autoRefresh: true,
      showAlerts: true,
    },
    defaultLayout: { x: 0, y: 0, w: 6, h: 5, minW: 4, minH: 4 },
    sampleData: {
      series: {
        id: 'sample',
        name: 'Sample Series',
        data: Array.from({ length: 50 }, (_, i) => ({
          timestamp: new Date(Date.now() - (49 - i) * 60 * 60 * 1000).toISOString(),
          value: 100 + Math.sin(i * 0.3) * 20 + (Math.random() - 0.5) * 10,
        })),
      },
      anomalies: [],
      sensitivity: 'medium',
    },
  },
  {
    id: 'ai-insights',
    name: 'AI Insights',
    description: 'Intelligent insights and recommendations',
    type: 'ai_insights',
    category: 'ml_analytics',
    thumbnail: '/images/widgets/ai-insights.png',
    defaultConfig: {
      showConfidence: true,
      autoGenerate: false,
      filterCritical: false,
    },
    defaultLayout: { x: 0, y: 0, w: 6, h: 6, minW: 4, minH: 4 },
    sampleData: {
      series: [],
      insights: [
        {
          id: '1',
          type: 'trend',
          title: 'Revenue Growth Detected',
          description: 'Revenue has increased by 15.3% over the last 7 days',
          severity: 'info',
          confidence: 0.85,
          timestamp: new Date().toISOString(),
        },
        {
          id: '2',
          type: 'anomaly',
          title: 'Unusual Activity Spike',
          description: 'User activity is 40% higher than expected',
          severity: 'warning',
          confidence: 0.92,
          timestamp: new Date().toISOString(),
        },
      ],
    },
  },
  {
    id: 'forecast',
    name: 'Forecast',
    description: 'Predictive analytics and forecasting',
    type: 'forecast',
    category: 'ml_analytics',
    thumbnail: '/images/widgets/forecast.png',
    defaultConfig: {
      horizon: 7,
      showConfidenceBands: true,
      seasonal: false,
    },
    defaultLayout: { x: 0, y: 0, w: 8, h: 5, minW: 6, minH: 4 },
    sampleData: {
      series: {
        id: 'sample',
        name: 'Sample Series',
        data: Array.from({ length: 30 }, (_, i) => ({
          timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
          value: 1000 + i * 10 + Math.sin(i * 0.2) * 50 + (Math.random() - 0.5) * 30,
        })),
      },
      horizon: 7,
    },
  },

  // Placeholder templates for other widget types
  {
    id: 'text-basic',
    name: 'Text Widget',
    description: 'Display formatted text content',
    type: 'text',
    category: 'content',
    thumbnail: '/images/widgets/text-basic.png',
    defaultConfig: {
      fontSize: 'md',
      alignment: 'left',
    },
    defaultLayout: { x: 0, y: 0, w: 4, h: 2, minW: 2, minH: 1 },
  },
  {
    id: 'image-basic',
    name: 'Image Widget',
    description: 'Display images with optional captions',
    type: 'image',
    category: 'content',
    thumbnail: '/images/widgets/image-basic.png',
    defaultConfig: {
      fit: 'cover',
      showCaption: false,
    },
    defaultLayout: { x: 0, y: 0, w: 4, h: 3, minW: 2, minH: 2 },
  },
];

// Helper functions
export const getWidgetComponent = (type: WidgetType): React.ComponentType<any> => {
  return widgetComponents[type] || widgetComponents.text;
};

export const getWidgetTemplate = (templateId: string): WidgetTemplate | undefined => {
  return widgetTemplates.find(template => template.id === templateId);
};

export const getWidgetTemplatesByCategory = (category: string) => {
  return widgetTemplates.filter(template => template.category === category);
};

export const createWidgetFromTemplate = (
  templateId: string,
  overrides: Partial<Widget> = {}
): Widget | null => {
  const template = getWidgetTemplate(templateId);
  if (!template) return null;

  return {
    id: `widget_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    type: template.type,
    title: template.name,
    description: template.description,
    config: { ...template.defaultConfig, ...overrides.config },
    layout: { ...template.defaultLayout, ...overrides.layout },
    data: template.sampleData,
    ...overrides,
  };
};

export const validateWidget = (widget: Widget): string[] => {
  const errors: string[] = [];

  if (!widget.id) errors.push('Widget must have an ID');
  if (!widget.title) errors.push('Widget must have a title');
  if (!widget.type) errors.push('Widget must have a type');
  if (!widgetComponents[widget.type]) errors.push(`Unsupported widget type: ${widget.type}`);
  if (!widget.layout) errors.push('Widget must have layout information');

  return errors;
};

// Widget categories for organization
export const widgetCategories = [
  { id: 'ml_analytics', name: 'AI & ML', icon: Brain },
  { id: 'metrics', name: 'Metrics', icon: Hash },
  { id: 'visualization', name: 'Charts', icon: BarChart3 },
  { id: 'content', name: 'Content', icon: Type },
  { id: 'interactive', name: 'Interactive', icon: Globe },
  { id: 'external', name: 'External', icon: Globe },
] as const;