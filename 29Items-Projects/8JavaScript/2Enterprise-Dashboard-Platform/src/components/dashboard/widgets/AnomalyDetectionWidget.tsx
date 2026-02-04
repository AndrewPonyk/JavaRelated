import React, { useState, useEffect, useMemo } from 'react';
import { AlertTriangle, TrendingUp, TrendingDown, Activity, Zap } from 'lucide-react';
import { Widget, WidgetData } from '@/types/dashboard';
import { Anomaly, TimeSeries } from '@/services/ml/types';
import { BaseWidget, WidgetContainer } from '@/components/dashboard/BaseWidget';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { mlService } from '@/services/ml/MLService';
import { useToastHelpers } from '@/components/ui/Toaster';
import { cn } from '@/utils/cn';

interface AnomalyDetectionWidgetProps {
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

interface AnomalyDetectionData {
  series: TimeSeries;
  anomalies: Anomaly[];
  isAnalyzing?: boolean;
  lastAnalyzed?: string;
  sensitivity: 'low' | 'medium' | 'high';
}

export const AnomalyDetectionWidget: React.FC<AnomalyDetectionWidgetProps> = ({
  widget,
  data,
  ...baseProps
}) => {
  const [localAnalyzing, setLocalAnalyzing] = useState(false);
  const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
  const { success: showSuccess, error: showError } = useToastHelpers();

  const anomalyData = data?.data as AnomalyDetectionData | undefined;
  const config = widget.config;

  const sensitivity = config.sensitivity || 'medium';

  useEffect(() => {
    if (anomalyData?.anomalies) {
      setAnomalies(anomalyData.anomalies);
    }
  }, [anomalyData]);

  const handleAnalyzeAnomalies = async () => {
    if (!anomalyData?.series) {
      showError('No data available', 'Cannot analyze anomalies without time series data');
      return;
    }

    setLocalAnalyzing(true);
    try {
      const detectedAnomalies = await mlService.detectAnomalies(anomalyData.series, {
        sensitivity,
      });

      setAnomalies(detectedAnomalies);
      showSuccess('Analysis Complete', `Found ${detectedAnomalies.length} anomalies`);
    } catch (error) {
      console.error('Anomaly detection failed:', error);
      showError('Analysis Failed', 'Unable to detect anomalies. Please try again.');
    } finally {
      setLocalAnalyzing(false);
    }
  };

  const anomalyStats = useMemo(() => {
    const severityGroups = anomalies.reduce((acc, anomaly) => {
      acc[anomaly.severity] = (acc[anomaly.severity] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    const typeGroups = anomalies.reduce((acc, anomaly) => {
      acc[anomaly.type] = (acc[anomaly.type] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    const recentAnomalies = anomalies.filter(a => {
      const anomalyTime = new Date(a.timestamp).getTime();
      const dayAgo = Date.now() - (24 * 60 * 60 * 1000);
      return anomalyTime > dayAgo;
    });

    return {
      total: anomalies.length,
      severityGroups,
      typeGroups,
      recent: recentAnomalies.length,
      avgScore: anomalies.length > 0
        ? anomalies.reduce((sum, a) => sum + a.score, 0) / anomalies.length
        : 0,
    };
  }, [anomalies]);

  const getSeverityIcon = (severity: string) => {
    switch (severity) {
      case 'critical':
        return <AlertTriangle className="h-4 w-4 text-red-500" />;
      case 'high':
        return <TrendingUp className="h-4 w-4 text-orange-500" />;
      case 'medium':
        return <Activity className="h-4 w-4 text-yellow-500" />;
      case 'low':
        return <TrendingDown className="h-4 w-4 text-blue-500" />;
      default:
        return <Activity className="h-4 w-4 text-gray-500" />;
    }
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical':
        return 'bg-red-100 text-red-800 border-red-200';
      case 'high':
        return 'bg-orange-100 text-orange-800 border-orange-200';
      case 'medium':
        return 'bg-yellow-100 text-yellow-800 border-yellow-200';
      case 'low':
        return 'bg-blue-100 text-blue-800 border-blue-200';
      default:
        return 'bg-gray-100 text-gray-800 border-gray-200';
    }
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'spike':
        return <TrendingUp className="h-3 w-3" />;
      case 'dip':
        return <TrendingDown className="h-3 w-3" />;
      case 'trend_change':
        return <Activity className="h-3 w-3" />;
      case 'seasonal_deviation':
        return <Zap className="h-3 w-3" />;
      default:
        return <AlertTriangle className="h-3 w-3" />;
    }
  };

  const renderAnomalyList = () => {
    if (anomalies.length === 0) {
      return (
        <div className="text-center py-8">
          <div className="text-green-500 mb-2">
            <Activity className="h-8 w-8 mx-auto" />
          </div>
          <p className="text-sm text-gray-600">No anomalies detected</p>
          <p className="text-xs text-gray-400 mt-1">Your data appears normal</p>
        </div>
      );
    }

    const recentAnomalies = anomalies
      .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
      .slice(0, 5);

    return (
      <div className="space-y-3">
        {recentAnomalies.map((anomaly, index) => (
          <div
            key={anomaly.id}
            className="flex items-start space-x-3 p-3 border border-gray-100 rounded-lg hover:bg-gray-50 transition-colors"
          >
            <div className="flex-shrink-0 mt-0.5">
              {getSeverityIcon(anomaly.severity)}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between mb-1">
                <Badge
                  variant="secondary"
                  className={cn('text-xs', getSeverityColor(anomaly.severity))}
                >
                  {anomaly.severity}
                </Badge>
                <span className="text-xs text-gray-400">
                  {new Date(anomaly.timestamp).toLocaleDateString()}
                </span>
              </div>
              <p className="text-sm text-gray-900 mb-1">
                {anomaly.description}
              </p>
              <div className="flex items-center space-x-4 text-xs text-gray-500">
                <span className="flex items-center space-x-1">
                  {getTypeIcon(anomaly.type)}
                  <span className="capitalize">{anomaly.type.replace('_', ' ')}</span>
                </span>
                <span>Score: {(anomaly.score * 100).toFixed(1)}%</span>
              </div>
            </div>
          </div>
        ))}

        {anomalies.length > 5 && (
          <div className="text-center">
            <Button variant="ghost" size="sm" className="text-blue-600">
              View {anomalies.length - 5} more anomalies
            </Button>
          </div>
        )}
      </div>
    );
  };

  const renderAnomalyStats = () => (
    <div className="grid grid-cols-2 gap-4 mb-4">
      <div className="bg-gray-50 rounded-lg p-3 text-center">
        <div className="text-lg font-bold text-gray-900">{anomalyStats.total}</div>
        <div className="text-xs text-gray-600">Total Anomalies</div>
      </div>
      <div className="bg-gray-50 rounded-lg p-3 text-center">
        <div className="text-lg font-bold text-orange-600">{anomalyStats.recent}</div>
        <div className="text-xs text-gray-600">Last 24h</div>
      </div>
    </div>
  );

  const isAnalyzing = localAnalyzing || anomalyData?.isAnalyzing || data?.loading;

  return (
    <BaseWidget widget={widget} data={data} {...baseProps}>
      <WidgetContainer
        loading={data?.loading}
        error={data?.error}
        empty={!anomalyData}
        emptyMessage="Configure data source to detect anomalies"
      >
        <div className="h-full flex flex-col">
          {/* Header with Controls */}
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center space-x-2">
              <AlertTriangle className="h-5 w-5 text-orange-500" />
              <span className="text-sm font-medium">Anomaly Detection</span>
            </div>
            <div className="flex items-center space-x-2">
              <Badge variant="outline" className="text-xs">
                {sensitivity} sensitivity
              </Badge>
              {!isAnalyzing && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleAnalyzeAnomalies}
                  disabled={!anomalyData?.series}
                >
                  <Zap className="h-3 w-3 mr-1" />
                  Analyze
                </Button>
              )}
            </div>
          </div>

          {isAnalyzing ? (
            <div className="flex-1 flex items-center justify-center">
              <div className="text-center">
                <div className="animate-spin rounded-full h-8 w-8 border-2 border-orange-500 border-t-transparent mx-auto mb-2" />
                <p className="text-sm text-gray-600">Analyzing data for anomalies...</p>
                <p className="text-xs text-gray-400 mt-1">This may take a few moments</p>
              </div>
            </div>
          ) : (
            <div className="flex-1 overflow-hidden">
              {/* Stats */}
              {anomalies.length > 0 && renderAnomalyStats()}

              {/* Anomaly List */}
              <div className="flex-1 overflow-y-auto">
                {renderAnomalyList()}
              </div>

              {/* Last Analysis Info */}
              {anomalyData?.lastAnalyzed && (
                <div className="mt-4 pt-3 border-t border-gray-100">
                  <p className="text-xs text-gray-400">
                    Last analyzed: {new Date(anomalyData.lastAnalyzed).toLocaleString()}
                  </p>
                </div>
              )}
            </div>
          )}
        </div>
      </WidgetContainer>
    </BaseWidget>
  );
};

export default AnomalyDetectionWidget;