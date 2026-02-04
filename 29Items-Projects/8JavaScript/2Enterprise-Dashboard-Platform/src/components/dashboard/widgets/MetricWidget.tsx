import React from 'react';
import { TrendingUp, TrendingDown, Minus, AlertTriangle } from 'lucide-react';
import { Widget, WidgetData } from '@/types/dashboard';
import { BaseWidget, WidgetContainer } from '@/components/dashboard/BaseWidget';
import { cn } from '@/utils/cn';

interface MetricWidgetProps {
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

interface MetricData {
  value: number;
  previousValue?: number;
  change?: number;
  changePercent?: number;
  trend?: 'up' | 'down' | 'neutral';
  target?: number;
  subtitle?: string;
}

export const MetricWidget: React.FC<MetricWidgetProps> = ({
  widget,
  data,
  ...baseProps
}) => {
  const metricData = data?.data as MetricData | undefined;
  const config = widget.config;

  const formatValue = (value: number | undefined) => {
    if (value === undefined || value === null) return '--';

    let formattedValue = value;

    // Apply precision
    if (config.precision !== undefined) {
      formattedValue = Number(value.toFixed(config.precision));
    }

    // Format large numbers
    if (Math.abs(formattedValue) >= 1000000) {
      formattedValue = formattedValue / 1000000;
      return `${config.prefix || ''}${formattedValue.toFixed(1)}M${config.suffix || ''}`;
    } else if (Math.abs(formattedValue) >= 1000) {
      formattedValue = formattedValue / 1000;
      return `${config.prefix || ''}${formattedValue.toFixed(1)}K${config.suffix || ''}`;
    }

    return `${config.prefix || ''}${formattedValue}${config.suffix || ''}`;
  };

  const getChangeIndicator = () => {
    if (!metricData?.change && metricData?.changePercent === undefined) {
      return null;
    }

    const changeValue = metricData.changePercent ?? metricData.change;
    const isPercent = metricData.changePercent !== undefined;
    const trend = metricData.trend || (changeValue && changeValue > 0 ? 'up' : changeValue < 0 ? 'down' : 'neutral');

    const getTrendIcon = () => {
      switch (trend) {
        case 'up':
          return <TrendingUp className="h-3 w-3" />;
        case 'down':
          return <TrendingDown className="h-3 w-3" />;
        default:
          return <Minus className="h-3 w-3" />;
      }
    };

    const getTrendColor = () => {
      switch (trend) {
        case 'up':
          return 'text-green-600';
        case 'down':
          return 'text-red-600';
        default:
          return 'text-gray-500';
      }
    };

    return (
      <div className={cn('flex items-center text-sm', getTrendColor())}>
        {getTrendIcon()}
        <span className="ml-1">
          {Math.abs(changeValue || 0).toFixed(1)}{isPercent ? '%' : ''}
        </span>
      </div>
    );
  };

  const getThresholdStatus = () => {
    if (!metricData?.value || !config.threshold) return null;

    const { warning, critical } = config.threshold;
    const value = metricData.value;

    if (critical && value >= critical) {
      return { level: 'critical', color: 'text-red-600', bgColor: 'bg-red-50' };
    } else if (warning && value >= warning) {
      return { level: 'warning', color: 'text-yellow-600', bgColor: 'bg-yellow-50' };
    }

    return null;
  };

  const thresholdStatus = getThresholdStatus();

  const getTargetProgress = () => {
    if (!metricData?.value || !metricData?.target) return null;

    const progress = (metricData.value / metricData.target) * 100;
    const isOnTrack = progress >= 80; // 80% of target considered on track

    return {
      percentage: Math.min(progress, 100),
      isOnTrack,
      color: isOnTrack ? 'bg-green-500' : progress >= 60 ? 'bg-yellow-500' : 'bg-red-500',
    };
  };

  const targetProgress = getTargetProgress();

  return (
    <BaseWidget widget={widget} data={data} {...baseProps}>
      <WidgetContainer
        loading={data?.loading}
        error={data?.error}
        empty={!metricData}
        emptyMessage="No metric data"
      >
        <div className={cn(
          'h-full flex flex-col justify-center',
          config.alignment === 'center' && 'text-center',
          config.alignment === 'right' && 'text-right',
          thresholdStatus?.bgColor
        )}>
          {/* Threshold Alert */}
          {thresholdStatus && (
            <div className={cn('flex items-center mb-2', thresholdStatus.color)}>
              <AlertTriangle className="h-4 w-4 mr-1" />
              <span className="text-xs capitalize">{thresholdStatus.level} threshold</span>
            </div>
          )}

          {/* Main Value */}
          <div className="flex-1 flex flex-col justify-center">
            <div className={cn(
              'font-bold tracking-tight',
              widget.layout.h <= 2 ? 'text-2xl' : widget.layout.h <= 3 ? 'text-3xl' : 'text-4xl',
              thresholdStatus?.color || 'text-gray-900'
            )}>
              {formatValue(metricData?.value)}
            </div>

            {/* Unit */}
            {config.unit && (
              <div className="text-sm text-gray-500 mt-1">{config.unit}</div>
            )}

            {/* Subtitle */}
            {metricData?.subtitle && (
              <div className="text-sm text-gray-600 mt-1">{metricData.subtitle}</div>
            )}
          </div>

          {/* Change Indicator */}
          <div className="flex items-center justify-between mt-2">
            {getChangeIndicator()}

            {/* Previous Value */}
            {metricData?.previousValue && (
              <div className="text-xs text-gray-400">
                was {formatValue(metricData.previousValue)}
              </div>
            )}
          </div>

          {/* Target Progress */}
          {targetProgress && (
            <div className="mt-3">
              <div className="flex items-center justify-between text-xs text-gray-500 mb-1">
                <span>Target Progress</span>
                <span>{targetProgress.percentage.toFixed(0)}%</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className={cn('h-2 rounded-full transition-all duration-300', targetProgress.color)}
                  style={{ width: `${targetProgress.percentage}%` }}
                />
              </div>
              {metricData.target && (
                <div className="text-xs text-gray-400 mt-1 text-right">
                  Target: {formatValue(metricData.target)}
                </div>
              )}
            </div>
          )}
        </div>
      </WidgetContainer>
    </BaseWidget>
  );
};

export default MetricWidget;