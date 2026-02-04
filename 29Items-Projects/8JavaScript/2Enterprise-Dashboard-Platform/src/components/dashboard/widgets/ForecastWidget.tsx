import React, { useState, useEffect, useMemo } from 'react';
import {
  TrendingUp,
  TrendingDown,
  Activity,
  Calendar,
  Target,
  AlertCircle,
  Zap,
  BarChart3
} from 'lucide-react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  Area,
  AreaChart,
  Legend
} from 'recharts';
import { Widget, WidgetData } from '@/types/dashboard';
import { Forecast, TimeSeries, DataPoint } from '@/services/ml/types';
import { BaseWidget, WidgetContainer } from '@/components/dashboard/BaseWidget';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { mlService } from '@/services/ml/MLService';
import { useToastHelpers } from '@/components/ui/Toaster';
import { cn } from '@/utils/cn';

interface ForecastWidgetProps {
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

interface ForecastData {
  series: TimeSeries;
  forecast?: Forecast;
  isForecasting?: boolean;
  lastForecasted?: string;
  horizon: number;
  showConfidenceBands?: boolean;
}

interface ChartDataPoint {
  timestamp: string;
  actual?: number;
  forecast?: number;
  lower?: number;
  upper?: number;
  type: 'historical' | 'forecast';
}

export const ForecastWidget: React.FC<ForecastWidgetProps> = ({
  widget,
  data,
  ...baseProps
}) => {
  const [localForecasting, setLocalForecasting] = useState(false);
  const [forecast, setForecast] = useState<Forecast | undefined>();
  const { success: showSuccess, error: showError } = useToastHelpers();

  const forecastData = data?.data as ForecastData | undefined;
  const config = widget.config;

  const horizon = config.horizon || 7;
  const showConfidenceBands = config.showConfidenceBands !== false;

  useEffect(() => {
    if (forecastData?.forecast) {
      setForecast(forecastData.forecast);
    }
  }, [forecastData]);

  const handleGenerateForecast = async () => {
    if (!forecastData?.series) {
      showError('No data available', 'Cannot generate forecast without time series data');
      return;
    }

    setLocalForecasting(true);
    try {
      const generatedForecast = await mlService.forecastTimeSeries(forecastData.series, {
        horizon,
        seasonal: config.seasonal,
        alpha: config.alpha,
        beta: config.beta,
        gamma: config.gamma,
      });

      setForecast(generatedForecast);
      showSuccess('Forecast Generated', `Generated ${horizon}-period forecast`);
    } catch (error) {
      console.error('Forecast generation failed:', error);
      showError('Forecast Failed', 'Unable to generate forecast. Please try again.');
    } finally {
      setLocalForecasting(false);
    }
  };

  const chartData = useMemo(() => {
    if (!forecastData?.series) return [];

    const data: ChartDataPoint[] = [];

    // Add historical data (last 30 points to keep chart readable)
    const historicalData = forecastData.series.data.slice(-30);
    historicalData.forEach(point => {
      data.push({
        timestamp: new Date(point.timestamp).toLocaleDateString(),
        actual: point.value,
        type: 'historical',
      });
    });

    // Add forecast data
    if (forecast?.predictions) {
      forecast.predictions.forEach(point => {
        const confidence = forecast.confidence || 0.7;
        const margin = point.value * (1 - confidence) * 0.5;

        data.push({
          timestamp: new Date(point.timestamp).toLocaleDateString(),
          forecast: point.value,
          lower: point.value - margin,
          upper: point.value + margin,
          type: 'forecast',
        });
      });
    }

    return data;
  }, [forecastData?.series, forecast]);

  const forecastStats = useMemo(() => {
    if (!forecast || !forecastData?.series) {
      return null;
    }

    const currentValue = forecastData.series.data[forecastData.series.data.length - 1]?.value || 0;
    const finalForecast = forecast.predictions[forecast.predictions.length - 1]?.value || 0;
    const changePercent = ((finalForecast - currentValue) / currentValue) * 100;

    const trend = changePercent > 5 ? 'up' : changePercent < -5 ? 'down' : 'stable';

    return {
      currentValue,
      finalForecast,
      changePercent: Math.abs(changePercent),
      trend,
      confidence: forecast.confidence,
      horizon: forecast.horizon,
      model: forecast.model,
    };
  }, [forecast, forecastData?.series]);

  const renderForecastChart = () => {
    if (!chartData.length) return null;

    const separatorIndex = chartData.findIndex(d => d.type === 'forecast');

    const customTooltip = ({ active, payload, label }: any) => {
      if (!active || !payload) return null;

      return (
        <div className="bg-white p-3 border border-gray-200 rounded-lg shadow-lg">
          <div className="font-medium text-gray-900 mb-1">{label}</div>
          {payload.map((entry: any, index: number) => (
            <div key={index} className="flex items-center text-sm">
              <div
                className="w-3 h-3 rounded-full mr-2"
                style={{ backgroundColor: entry.color }}
              />
              <span className="text-gray-600">{entry.dataKey}:</span>
              <span className="font-medium ml-1">{entry.value?.toFixed(2)}</span>
            </div>
          ))}
        </div>
      );
    };

    return (
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 10, bottom: 10 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
          <XAxis
            dataKey="timestamp"
            tick={{ fontSize: 10 }}
            axisLine={{ stroke: '#e5e7eb' }}
            tickLine={{ stroke: '#e5e7eb' }}
          />
          <YAxis
            tick={{ fontSize: 10 }}
            axisLine={{ stroke: '#e5e7eb' }}
            tickLine={{ stroke: '#e5e7eb' }}
          />
          <Tooltip content={customTooltip} />
          <Legend />

          {/* Confidence bands */}
          {showConfidenceBands && (
            <Area
              dataKey="upper"
              stroke="none"
              fill="#dbeafe"
              fillOpacity={0.3}
              strokeWidth={0}
            />
          )}
          {showConfidenceBands && (
            <Area
              dataKey="lower"
              stroke="none"
              fill="#ffffff"
              fillOpacity={1}
              strokeWidth={0}
            />
          )}

          {/* Actual data line */}
          <Line
            dataKey="actual"
            stroke="#3b82f6"
            strokeWidth={2}
            dot={{ r: 3 }}
            name="Actual"
            connectNulls={false}
          />

          {/* Forecast line */}
          <Line
            dataKey="forecast"
            stroke="#f59e0b"
            strokeWidth={2}
            strokeDasharray="5 5"
            dot={{ r: 3 }}
            name="Forecast"
            connectNulls={false}
          />

          {/* Separator line */}
          {separatorIndex > 0 && (
            <ReferenceLine
              x={chartData[separatorIndex - 1]?.timestamp}
              stroke="#9ca3af"
              strokeDasharray="2 2"
            />
          )}
        </AreaChart>
      </ResponsiveContainer>
    );
  };

  const renderForecastSummary = () => {
    if (!forecastStats) return null;

    const getTrendIcon = () => {
      switch (forecastStats.trend) {
        case 'up':
          return <TrendingUp className="h-4 w-4 text-green-500" />;
        case 'down':
          return <TrendingDown className="h-4 w-4 text-red-500" />;
        default:
          return <Activity className="h-4 w-4 text-gray-500" />;
      }
    };

    const getTrendColor = () => {
      switch (forecastStats.trend) {
        case 'up':
          return 'text-green-600';
        case 'down':
          return 'text-red-600';
        default:
          return 'text-gray-600';
      }
    };

    const getConfidenceColor = () => {
      if (forecastStats.confidence > 0.8) return 'text-green-600';
      if (forecastStats.confidence > 0.6) return 'text-yellow-600';
      return 'text-red-600';
    };

    return (
      <div className="space-y-3">
        {/* Current vs Forecast */}
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-blue-50 rounded-lg p-3 text-center">
            <div className="text-sm font-medium text-blue-800 mb-1">Current</div>
            <div className="text-lg font-bold text-blue-900">
              {forecastStats.currentValue.toFixed(2)}
            </div>
          </div>
          <div className="bg-orange-50 rounded-lg p-3 text-center">
            <div className="text-sm font-medium text-orange-800 mb-1">
              {forecastStats.horizon}-Period Forecast
            </div>
            <div className="text-lg font-bold text-orange-900">
              {forecastStats.finalForecast.toFixed(2)}
            </div>
          </div>
        </div>

        {/* Trend and Change */}
        <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div className="flex items-center space-x-2">
            {getTrendIcon()}
            <span className={cn('text-sm font-medium', getTrendColor())}>
              {forecastStats.trend === 'stable'
                ? 'Stable Trend'
                : `${forecastStats.changePercent.toFixed(1)}% ${
                    forecastStats.trend === 'up' ? 'Increase' : 'Decrease'
                  }`}
            </span>
          </div>
          <Badge variant="outline" className={getConfidenceColor()}>
            {(forecastStats.confidence * 100).toFixed(0)}% confidence
          </Badge>
        </div>

        {/* Model Info */}
        <div className="text-xs text-gray-500 space-y-1">
          <div className="flex justify-between">
            <span>Model:</span>
            <span className="capitalize">{forecastStats.model.replace('_', ' ')}</span>
          </div>
          <div className="flex justify-between">
            <span>Horizon:</span>
            <span>{forecastStats.horizon} periods</span>
          </div>
        </div>
      </div>
    );
  };

  const isForecasting = localForecasting || forecastData?.isForecasting || data?.loading;

  return (
    <BaseWidget widget={widget} data={data} {...baseProps}>
      <WidgetContainer
        loading={data?.loading}
        error={data?.error}
        empty={!forecastData}
        emptyMessage="Configure time series data to generate forecasts"
      >
        <div className="h-full flex flex-col">
          {/* Header with Controls */}
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center space-x-2">
              <Target className="h-5 w-5 text-orange-500" />
              <span className="text-sm font-medium">Forecast</span>
            </div>
            <div className="flex items-center space-x-2">
              <Badge variant="outline" className="text-xs">
                {horizon} periods
              </Badge>
              {!isForecasting && (
                <Button
                  size="sm"
                  onClick={handleGenerateForecast}
                  disabled={!forecastData?.series}
                >
                  <Zap className="h-3 w-3 mr-1" />
                  Forecast
                </Button>
              )}
            </div>
          </div>

          {isForecasting ? (
            <div className="flex-1 flex items-center justify-center">
              <div className="text-center">
                <div className="animate-spin rounded-full h-8 w-8 border-2 border-orange-500 border-t-transparent mx-auto mb-2" />
                <p className="text-sm text-gray-600">Generating forecast...</p>
                <p className="text-xs text-gray-400 mt-1">Analyzing trends and patterns</p>
              </div>
            </div>
          ) : forecast ? (
            <div className="flex-1 overflow-hidden">
              {/* Chart */}
              <div className="h-48 mb-4">
                {renderForecastChart()}
              </div>

              {/* Summary */}
              <div className="flex-1 overflow-y-auto">
                {renderForecastSummary()}
              </div>

              {/* Confidence Warning */}
              {forecast.confidence < 0.6 && (
                <div className="mt-4 p-2 bg-yellow-50 border border-yellow-200 rounded-lg">
                  <div className="flex items-start space-x-2">
                    <AlertCircle className="h-4 w-4 text-yellow-600 mt-0.5 flex-shrink-0" />
                    <div className="text-xs text-yellow-800">
                      <div className="font-medium">Low Confidence</div>
                      <div>This forecast has low confidence. Consider collecting more data or reviewing data quality.</div>
                    </div>
                  </div>
                </div>
              )}

              {/* Last Forecast Info */}
              {forecastData?.lastForecasted && (
                <div className="mt-4 pt-3 border-t border-gray-100">
                  <p className="text-xs text-gray-400 flex items-center">
                    <Calendar className="h-3 w-3 mr-1" />
                    Last forecasted: {new Date(forecastData.lastForecasted).toLocaleString()}
                  </p>
                </div>
              )}
            </div>
          ) : (
            <div className="flex-1 flex items-center justify-center">
              <div className="text-center text-gray-400">
                <BarChart3 className="h-12 w-12 mx-auto mb-3" />
                <p className="text-sm font-medium mb-1">No Forecast Available</p>
                <p className="text-xs mb-4">Generate a forecast to see future predictions</p>
                <Button
                  size="sm"
                  onClick={handleGenerateForecast}
                  disabled={!forecastData?.series}
                >
                  <Target className="h-3 w-3 mr-1" />
                  Generate Forecast
                </Button>
              </div>
            </div>
          )}
        </div>
      </WidgetContainer>
    </BaseWidget>
  );
};

export default ForecastWidget;