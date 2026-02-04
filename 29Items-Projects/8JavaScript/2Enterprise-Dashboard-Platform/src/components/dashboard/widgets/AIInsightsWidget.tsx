import React, { useState, useEffect, useMemo } from 'react';
import {
  Brain,
  TrendingUp,
  AlertTriangle,
  Info,
  Lightbulb,
  Eye,
  EyeOff,
  ExternalLink,
  Star,
  Clock,
  CheckCircle,
  X
} from 'lucide-react';
import { Widget, WidgetData } from '@/types/dashboard';
import { Insight, InsightAction, TimeSeries } from '@/services/ml/types';
import { BaseWidget, WidgetContainer } from '@/components/dashboard/BaseWidget';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { insightsEngine } from '@/services/ml/InsightsEngine';
import { useToastHelpers } from '@/components/ui/Toaster';
import { cn } from '@/utils/cn';

interface AIInsightsWidgetProps {
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

interface AIInsightsData {
  series: TimeSeries[];
  insights: Insight[];
  isGenerating?: boolean;
  lastGenerated?: string;
  filters?: {
    severity?: ('info' | 'warning' | 'critical')[];
    type?: string[];
    isRead?: boolean;
  };
}

export const AIInsightsWidget: React.FC<AIInsightsWidgetProps> = ({
  widget,
  data,
  ...baseProps
}) => {
  const [localGenerating, setLocalGenerating] = useState(false);
  const [insights, setInsights] = useState<Insight[]>([]);
  const [filter, setFilter] = useState<'all' | 'unread' | 'critical'>('all');
  const [dismissedInsights, setDismissedInsights] = useState<Set<string>>(new Set());
  const { success: showSuccess, error: showError } = useToastHelpers();

  const insightsData = data?.data as AIInsightsData | undefined;
  const config = widget.config;

  useEffect(() => {
    if (insightsData?.insights) {
      setInsights(insightsData.insights);
    }
  }, [insightsData]);

  const handleGenerateInsights = async () => {
    if (!insightsData?.series?.length) {
      showError('No data available', 'Cannot generate insights without time series data');
      return;
    }

    setLocalGenerating(true);
    try {
      const generatedInsights = await insightsEngine.generateInsights(insightsData.series, {
        includeAnomalies: true,
        includeForecasts: true,
        includePatterns: true,
        includeCorrelations: true,
      });

      setInsights(generatedInsights);
      showSuccess('Insights Generated', `Found ${generatedInsights.length} insights`);
    } catch (error) {
      console.error('Insight generation failed:', error);
      showError('Generation Failed', 'Unable to generate insights. Please try again.');
    } finally {
      setLocalGenerating(false);
    }
  };

  const handleDismissInsight = (insightId: string) => {
    setDismissedInsights(prev => new Set(prev).add(insightId));
  };

  const handleMarkAsRead = (insightId: string) => {
    setInsights(prev => prev.map(insight =>
      insight.id === insightId
        ? { ...insight, isRead: true }
        : insight
    ));
  };

  const filteredInsights = useMemo(() => {
    let filtered = insights.filter(insight => !dismissedInsights.has(insight.id));

    switch (filter) {
      case 'unread':
        filtered = filtered.filter(insight => !insight.isRead);
        break;
      case 'critical':
        filtered = filtered.filter(insight => insight.severity === 'critical');
        break;
      default:
        break;
    }

    return filtered;
  }, [insights, filter, dismissedInsights]);

  const insightStats = useMemo(() => {
    const severityGroups = insights.reduce((acc, insight) => {
      if (!dismissedInsights.has(insight.id)) {
        acc[insight.severity] = (acc[insight.severity] || 0) + 1;
      }
      return acc;
    }, {} as Record<string, number>);

    const typeGroups = insights.reduce((acc, insight) => {
      if (!dismissedInsights.has(insight.id)) {
        acc[insight.type] = (acc[insight.type] || 0) + 1;
      }
      return acc;
    }, {} as Record<string, number>);

    const unreadCount = insights.filter(
      insight => !insight.isRead && !dismissedInsights.has(insight.id)
    ).length;

    return {
      total: insights.length - dismissedInsights.size,
      severityGroups,
      typeGroups,
      unread: unreadCount,
      avgConfidence: insights.length > 0
        ? insights
            .filter(insight => !dismissedInsights.has(insight.id))
            .reduce((sum, insight) => sum + insight.confidence, 0) /
          Math.max(1, insights.length - dismissedInsights.size)
        : 0,
    };
  }, [insights, dismissedInsights]);

  const getSeverityIcon = (severity: string) => {
    switch (severity) {
      case 'critical':
        return <AlertTriangle className="h-4 w-4 text-red-500" />;
      case 'warning':
        return <AlertTriangle className="h-4 w-4 text-yellow-500" />;
      case 'info':
        return <Info className="h-4 w-4 text-blue-500" />;
      default:
        return <Info className="h-4 w-4 text-gray-500" />;
    }
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical':
        return 'bg-red-100 text-red-800 border-red-200';
      case 'warning':
        return 'bg-yellow-100 text-yellow-800 border-yellow-200';
      case 'info':
        return 'bg-blue-100 text-blue-800 border-blue-200';
      default:
        return 'bg-gray-100 text-gray-800 border-gray-200';
    }
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'trend':
        return <TrendingUp className="h-3 w-3" />;
      case 'anomaly':
        return <AlertTriangle className="h-3 w-3" />;
      case 'correlation':
        return <Star className="h-3 w-3" />;
      case 'recommendation':
        return <Lightbulb className="h-3 w-3" />;
      default:
        return <Info className="h-3 w-3" />;
    }
  };

  const handleInsightAction = (action: InsightAction) => {
    switch (action.type) {
      case 'link':
        if (action.url) {
          window.open(action.url, '_blank');
        }
        break;
      case 'action':
        // Handle custom actions
        showSuccess('Action Triggered', `${action.label} action executed`);
        break;
      case 'dismiss':
        // Handle dismiss action if implemented
        break;
    }
  };

  const renderInsightsList = () => {
    if (filteredInsights.length === 0) {
      return (
        <div className="text-center py-8">
          <div className="text-blue-500 mb-2">
            <Brain className="h-8 w-8 mx-auto" />
          </div>
          <p className="text-sm text-gray-600">
            {filter === 'all' ? 'No insights available' : `No ${filter} insights`}
          </p>
          <p className="text-xs text-gray-400 mt-1">
            {insights.length === 0 ? 'Generate insights to see recommendations' : 'Try changing the filter'}
          </p>
        </div>
      );
    }

    return (
      <div className="space-y-3">
        {filteredInsights.slice(0, 10).map((insight) => (
          <div
            key={insight.id}
            className={cn(
              'relative p-4 border rounded-lg transition-all',
              insight.isRead
                ? 'border-gray-200 bg-gray-50'
                : 'border-gray-300 bg-white hover:border-blue-200 hover:bg-blue-50'
            )}
          >
            {/* Dismiss Button */}
            <button
              onClick={() => handleDismissInsight(insight.id)}
              className="absolute top-2 right-2 text-gray-400 hover:text-gray-600 transition-colors"
            >
              <X className="h-3 w-3" />
            </button>

            <div className="flex items-start space-x-3">
              <div className="flex-shrink-0 mt-0.5">
                {getSeverityIcon(insight.severity)}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center space-x-2">
                    <Badge
                      variant="secondary"
                      className={cn('text-xs', getSeverityColor(insight.severity))}
                    >
                      {insight.severity}
                    </Badge>
                    <Badge variant="outline" className="text-xs">
                      <span className="flex items-center space-x-1">
                        {getTypeIcon(insight.type)}
                        <span className="capitalize">{insight.type}</span>
                      </span>
                    </Badge>
                    {!insight.isRead && (
                      <div className="w-2 h-2 bg-blue-500 rounded-full" />
                    )}
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs text-gray-400 flex items-center">
                      <Star className="h-3 w-3 mr-1" />
                      {(insight.confidence * 100).toFixed(0)}%
                    </span>
                    <span className="text-xs text-gray-400">
                      {new Date(insight.timestamp).toLocaleDateString()}
                    </span>
                  </div>
                </div>

                <h4 className="font-medium text-gray-900 mb-1">
                  {insight.title}
                </h4>
                <p className="text-sm text-gray-600 mb-3">
                  {insight.description}
                </p>

                {/* Actions */}
                {insight.actions && insight.actions.length > 0 && (
                  <div className="flex flex-wrap gap-2 mb-2">
                    {insight.actions.map((action) => (
                      <Button
                        key={action.id}
                        size="sm"
                        variant="outline"
                        onClick={() => handleInsightAction(action)}
                        className="text-xs"
                      >
                        {action.type === 'link' && (
                          <ExternalLink className="h-3 w-3 mr-1" />
                        )}
                        {action.label}
                      </Button>
                    ))}
                  </div>
                )}

                {/* Mark as Read */}
                {!insight.isRead && (
                  <button
                    onClick={() => handleMarkAsRead(insight.id)}
                    className="text-xs text-blue-600 hover:text-blue-700 flex items-center"
                  >
                    <CheckCircle className="h-3 w-3 mr-1" />
                    Mark as read
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}

        {filteredInsights.length > 10 && (
          <div className="text-center">
            <Button variant="ghost" size="sm" className="text-blue-600">
              View {filteredInsights.length - 10} more insights
            </Button>
          </div>
        )}
      </div>
    );
  };

  const renderInsightsStats = () => (
    <div className="grid grid-cols-3 gap-3 mb-4">
      <div className="bg-gray-50 rounded-lg p-3 text-center">
        <div className="text-lg font-bold text-gray-900">{insightStats.total}</div>
        <div className="text-xs text-gray-600">Total</div>
      </div>
      <div className="bg-blue-50 rounded-lg p-3 text-center">
        <div className="text-lg font-bold text-blue-600">{insightStats.unread}</div>
        <div className="text-xs text-gray-600">Unread</div>
      </div>
      <div className="bg-green-50 rounded-lg p-3 text-center">
        <div className="text-lg font-bold text-green-600">
          {(insightStats.avgConfidence * 100).toFixed(0)}%
        </div>
        <div className="text-xs text-gray-600">Confidence</div>
      </div>
    </div>
  );

  const isGenerating = localGenerating || insightsData?.isGenerating || data?.loading;

  return (
    <BaseWidget widget={widget} data={data} {...baseProps}>
      <WidgetContainer
        loading={data?.loading}
        error={data?.error}
        empty={!insightsData}
        emptyMessage="Configure data sources to generate AI insights"
      >
        <div className="h-full flex flex-col">
          {/* Header with Controls */}
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center space-x-2">
              <Brain className="h-5 w-5 text-blue-500" />
              <span className="text-sm font-medium">AI Insights</span>
            </div>
            <div className="flex items-center space-x-2">
              <select
                value={filter}
                onChange={(e) => setFilter(e.target.value as any)}
                className="text-xs border border-gray-300 rounded px-2 py-1"
              >
                <option value="all">All</option>
                <option value="unread">Unread</option>
                <option value="critical">Critical</option>
              </select>
              {!isGenerating && (
                <Button
                  size="sm"
                  onClick={handleGenerateInsights}
                  disabled={!insightsData?.series?.length}
                >
                  <Brain className="h-3 w-3 mr-1" />
                  Generate
                </Button>
              )}
            </div>
          </div>

          {isGenerating ? (
            <div className="flex-1 flex items-center justify-center">
              <div className="text-center">
                <div className="animate-spin rounded-full h-8 w-8 border-2 border-blue-500 border-t-transparent mx-auto mb-2" />
                <p className="text-sm text-gray-600">Generating AI insights...</p>
                <p className="text-xs text-gray-400 mt-1">Analyzing patterns and trends</p>
              </div>
            </div>
          ) : (
            <div className="flex-1 overflow-hidden">
              {/* Stats */}
              {insights.length > 0 && renderInsightsStats()}

              {/* Insights List */}
              <div className="flex-1 overflow-y-auto">
                {renderInsightsList()}
              </div>

              {/* Last Generation Info */}
              {insightsData?.lastGenerated && (
                <div className="mt-4 pt-3 border-t border-gray-100">
                  <p className="text-xs text-gray-400 flex items-center">
                    <Clock className="h-3 w-3 mr-1" />
                    Last generated: {new Date(insightsData.lastGenerated).toLocaleString()}
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

export default AIInsightsWidget;