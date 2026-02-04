import React, { useMemo } from 'react';
import {
  ResponsiveContainer,
  LineChart,
  BarChart,
  AreaChart,
  PieChart,
  ScatterChart,
  Line,
  Bar,
  Area,
  Pie,
  Cell,
  Scatter,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainerProps,
} from 'recharts';
import { Widget, WidgetData, ChartType } from '@/types/dashboard';
import { BaseWidget, WidgetContainer } from '@/components/dashboard/BaseWidget';
import { cn } from '@/utils/cn';

interface ChartWidgetProps {
  widget: Widget;
  data?: WidgetData;
  isEditing?: boolean;
  isSelected?: boolean;
  isDragging?: boolean;
  onUpdate?: (widget: Widget) => void;
  onDelete?: (widgetId: string) => void;
  onDuplicate?: (widget: Widget) => void;
  onRefresh?: (widgetId: string) => void;
  onSettings?: (widget: Widget) => void;
  onShare?: (widget: Widget) => void;
  onFullscreen?: (widget: Widget) => void;
}

interface ChartDataPoint {
  [key: string]: any;
}

const DEFAULT_COLORS = [
  '#3b82f6', // blue
  '#10b981', // emerald
  '#f59e0b', // amber
  '#ef4444', // red
  '#8b5cf6', // violet
  '#06b6d4', // cyan
  '#84cc16', // lime
  '#ec4899', // pink
  '#6b7280', // gray
  '#14b8a6', // teal
];

export const ChartWidget: React.FC<ChartWidgetProps> = ({
  widget,
  data,
  ...baseProps
}) => {
  const chartData = data?.data as ChartDataPoint[] | undefined;
  const config = widget.config;
  const chartType = config.chartType as ChartType || 'line';

  const colors = config.colors || DEFAULT_COLORS;

  const processedData = useMemo(() => {
    if (!chartData || !Array.isArray(chartData)) return [];

    return chartData.map((item, index) => ({
      ...item,
      index,
    }));
  }, [chartData]);

  const getDataKeys = () => {
    if (!processedData.length) return [];

    const firstItem = processedData[0];
    return Object.keys(firstItem).filter(key =>
      key !== 'index' &&
      typeof firstItem[key] === 'number'
    );
  };

  const dataKeys = getDataKeys();
  const xAxisKey = Object.keys(processedData[0] || {}).find(key =>
    key !== 'index' && typeof (processedData[0] || {})[key] === 'string'
  ) || 'index';

  const renderChart = () => {
    if (!processedData.length) {
      return (
        <div className="flex items-center justify-center h-full text-gray-400">
          <div className="text-center">
            <div className="text-lg mb-2">📊</div>
            <div className="text-sm">No chart data available</div>
          </div>
        </div>
      );
    }

    const commonProps = {
      data: processedData,
      margin: { top: 5, right: 5, left: 5, bottom: 5 },
    };

    const commonAxisProps = {
      tick: { fontSize: 12 },
      axisLine: { stroke: '#e5e7eb' },
      tickLine: { stroke: '#e5e7eb' },
    };

    const renderTooltip = (props: any) => {
      if (!props.active || !props.payload) return null;

      return (
        <div className="bg-white p-3 border border-gray-200 rounded-lg shadow-lg">
          {props.label && (
            <div className="font-medium text-gray-900 mb-1">{props.label}</div>
          )}
          {props.payload.map((entry: any, index: number) => (
            <div key={index} className="flex items-center text-sm">
              <div
                className="w-3 h-3 rounded-full mr-2"
                style={{ backgroundColor: entry.color }}
              />
              <span className="text-gray-600">{entry.dataKey}:</span>
              <span className="font-medium ml-1">{entry.value}</span>
            </div>
          ))}
        </div>
      );
    };

    switch (chartType) {
      case 'line':
        return (
          <LineChart {...commonProps}>
            {config.showGrid && <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />}
            <XAxis
              dataKey={xAxisKey}
              {...commonAxisProps}
              label={config.xAxisLabel ? { value: config.xAxisLabel, position: 'insideBottom', offset: -10 } : undefined}
            />
            <YAxis
              {...commonAxisProps}
              label={config.yAxisLabel ? { value: config.yAxisLabel, angle: -90, position: 'insideLeft' } : undefined}
            />
            <Tooltip content={renderTooltip} />
            {config.showLegend && <Legend />}
            {dataKeys.map((key, index) => (
              <Line
                key={key}
                type="monotone"
                dataKey={key}
                stroke={colors[index % colors.length]}
                strokeWidth={2}
                dot={{ r: 3 }}
                activeDot={{ r: 5 }}
              />
            ))}
          </LineChart>
        );

      case 'bar':
        return (
          <BarChart {...commonProps}>
            {config.showGrid && <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />}
            <XAxis
              dataKey={xAxisKey}
              {...commonAxisProps}
              label={config.xAxisLabel ? { value: config.xAxisLabel, position: 'insideBottom', offset: -10 } : undefined}
            />
            <YAxis
              {...commonAxisProps}
              label={config.yAxisLabel ? { value: config.yAxisLabel, angle: -90, position: 'insideLeft' } : undefined}
            />
            <Tooltip content={renderTooltip} />
            {config.showLegend && <Legend />}
            {dataKeys.map((key, index) => (
              <Bar
                key={key}
                dataKey={key}
                fill={colors[index % colors.length]}
                radius={[2, 2, 0, 0]}
              />
            ))}
          </BarChart>
        );

      case 'area':
        return (
          <AreaChart {...commonProps}>
            {config.showGrid && <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />}
            <XAxis
              dataKey={xAxisKey}
              {...commonAxisProps}
              label={config.xAxisLabel ? { value: config.xAxisLabel, position: 'insideBottom', offset: -10 } : undefined}
            />
            <YAxis
              {...commonAxisProps}
              label={config.yAxisLabel ? { value: config.yAxisLabel, angle: -90, position: 'insideLeft' } : undefined}
            />
            <Tooltip content={renderTooltip} />
            {config.showLegend && <Legend />}
            {dataKeys.map((key, index) => (
              <Area
                key={key}
                type="monotone"
                dataKey={key}
                stackId="1"
                stroke={colors[index % colors.length]}
                fill={colors[index % colors.length]}
                fillOpacity={0.3}
              />
            ))}
          </AreaChart>
        );

      case 'pie':
      case 'doughnut':
        const pieData = dataKeys.length === 1
          ? processedData.map(item => ({
              name: item[xAxisKey],
              value: item[dataKeys[0]],
            }))
          : dataKeys.map((key, index) => ({
              name: key,
              value: processedData.reduce((sum, item) => sum + (item[key] || 0), 0),
            }));

        return (
          <PieChart {...commonProps}>
            <Pie
              data={pieData}
              cx="50%"
              cy="50%"
              innerRadius={chartType === 'doughnut' ? 40 : 0}
              outerRadius={Math.min(widget.layout.w * 20, widget.layout.h * 15)}
              paddingAngle={2}
              dataKey="value"
            >
              {pieData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={colors[index % colors.length]} />
              ))}
            </Pie>
            <Tooltip
              content={({ active, payload }) => {
                if (!active || !payload?.[0]) return null;
                const data = payload[0].payload;
                return (
                  <div className="bg-white p-3 border border-gray-200 rounded-lg shadow-lg">
                    <div className="flex items-center">
                      <div
                        className="w-3 h-3 rounded-full mr-2"
                        style={{ backgroundColor: payload[0].payload.fill }}
                      />
                      <span className="font-medium">{data.name}: {data.value}</span>
                    </div>
                  </div>
                );
              }}
            />
            {config.showLegend && <Legend />}
          </PieChart>
        );

      case 'scatter':
        return (
          <ScatterChart {...commonProps}>
            {config.showGrid && <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />}
            <XAxis
              type="number"
              dataKey={dataKeys[0] || xAxisKey}
              {...commonAxisProps}
              label={config.xAxisLabel ? { value: config.xAxisLabel, position: 'insideBottom', offset: -10 } : undefined}
            />
            <YAxis
              type="number"
              dataKey={dataKeys[1] || dataKeys[0]}
              {...commonAxisProps}
              label={config.yAxisLabel ? { value: config.yAxisLabel, angle: -90, position: 'insideLeft' } : undefined}
            />
            <Tooltip content={renderTooltip} />
            {config.showLegend && <Legend />}
            <Scatter
              name="Data"
              data={processedData}
              fill={colors[0]}
            />
          </ScatterChart>
        );

      default:
        return (
          <div className="flex items-center justify-center h-full text-gray-400">
            <div className="text-center">
              <div className="text-lg mb-2">📊</div>
              <div className="text-sm">Chart type "{chartType}" not supported</div>
            </div>
          </div>
        );
    }
  };

  return (
    <BaseWidget widget={widget} data={data} {...baseProps}>
      <WidgetContainer
        loading={data?.loading}
        error={data?.error}
        empty={!chartData?.length}
        emptyMessage="No chart data available"
      >
        <div className="h-full w-full">
          <ResponsiveContainer width="100%" height="100%">
            {renderChart()}
          </ResponsiveContainer>
        </div>
      </WidgetContainer>
    </BaseWidget>
  );
};

export default ChartWidget;