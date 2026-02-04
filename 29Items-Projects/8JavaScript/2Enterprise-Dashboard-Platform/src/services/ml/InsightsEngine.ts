import {
  TimeSeries,
  Anomaly,
  Forecast,
  PatternAnalysis,
  CorrelationAnalysis,
  Insight,
  InsightAction,
  Alert,
  AlertRule,
  MLError
} from './types';
import { mlService } from './MLService';

export class InsightsEngine {
  private static instance: InsightsEngine;

  private constructor() {}

  static getInstance(): InsightsEngine {
    if (!InsightsEngine.instance) {
      InsightsEngine.instance = new InsightsEngine();
    }
    return InsightsEngine.instance;
  }

  // Generate comprehensive insights from multiple data series
  async generateInsights(
    series: TimeSeries[],
    options: {
      includeAnomalies?: boolean;
      includeForecasts?: boolean;
      includePatterns?: boolean;
      includeCorrelations?: boolean;
      timeWindow?: number; // days
    } = {}
  ): Promise<Insight[]> {
    const {
      includeAnomalies = true,
      includeForecasts = true,
      includePatterns = true,
      includeCorrelations = true,
      timeWindow = 30
    } = options;

    const insights: Insight[] = [];

    try {
      // Process each series individually
      for (const serie of series) {
        if (includeAnomalies) {
          const anomalyInsights = await this.generateAnomalyInsights(serie, timeWindow);
          insights.push(...anomalyInsights);
        }

        if (includeForecasts) {
          const forecastInsights = await this.generateForecastInsights(serie);
          insights.push(...forecastInsights);
        }

        if (includePatterns) {
          const patternInsights = await this.generatePatternInsights(serie);
          insights.push(...patternInsights);
        }
      }

      // Process correlations between series
      if (includeCorrelations && series.length > 1) {
        const correlationInsights = await this.generateCorrelationInsights(series);
        insights.push(...correlationInsights);
      }

      // Generate performance insights
      const performanceInsights = await this.generatePerformanceInsights(series);
      insights.push(...performanceInsights);

      // Generate optimization recommendations
      const optimizationInsights = await this.generateOptimizationInsights(series);
      insights.push(...optimizationInsights);

      // Sort by confidence and severity
      return insights.sort((a, b) => {
        const severityOrder = { critical: 3, warning: 2, info: 1 };
        const aSeverity = severityOrder[a.severity];
        const bSeverity = severityOrder[b.severity];

        if (aSeverity !== bSeverity) {
          return bSeverity - aSeverity;
        }

        return b.confidence - a.confidence;
      });

    } catch (error) {
      console.error('Failed to generate insights:', error);
      throw new MLError({
        code: 'INSIGHTS_GENERATION_FAILED',
        message: 'Failed to generate insights',
        details: error,
        timestamp: new Date().toISOString(),
      });
    }
  }

  // Generate anomaly-based insights
  private async generateAnomalyInsights(series: TimeSeries, timeWindow: number): Promise<Insight[]> {
    const insights: Insight[] = [];

    try {
      const anomalies = await mlService.detectAnomalies(series, { sensitivity: 'medium' });

      if (anomalies.length === 0) {
        insights.push({
          id: `insight_normal_${series.id}_${Date.now()}`,
          type: 'anomaly',
          title: `${series.name} Shows Normal Behavior`,
          description: `No significant anomalies detected in the last ${timeWindow} days. The data follows expected patterns.`,
          severity: 'info',
          confidence: 0.8,
          timestamp: new Date().toISOString(),
          data: { seriesId: series.id, anomalyCount: 0 },
        });
      } else {
        const criticalAnomalies = anomalies.filter(a => a.severity === 'critical');
        const recentAnomalies = anomalies.filter(a => {
          const anomalyTime = new Date(a.timestamp).getTime();
          const cutoffTime = Date.now() - (timeWindow * 24 * 60 * 60 * 1000);
          return anomalyTime > cutoffTime;
        });

        if (criticalAnomalies.length > 0) {
          insights.push({
            id: `insight_critical_anomalies_${series.id}_${Date.now()}`,
            type: 'anomaly',
            title: `Critical Anomalies Detected in ${series.name}`,
            description: `${criticalAnomalies.length} critical anomal${criticalAnomalies.length > 1 ? 'ies' : 'y'} detected. Immediate attention may be required.`,
            severity: 'critical',
            confidence: 0.9,
            timestamp: new Date().toISOString(),
            data: {
              seriesId: series.id,
              anomalies: criticalAnomalies,
              totalCount: anomalies.length,
            },
            actions: [
              {
                id: 'view_anomalies',
                label: 'View Anomalies',
                type: 'link',
                url: `/analytics/anomalies?series=${series.id}`,
              },
              {
                id: 'create_alert',
                label: 'Create Alert Rule',
                type: 'action',
                action: 'create_alert_rule',
                parameters: { seriesId: series.id, type: 'anomaly' },
              },
            ],
          });
        }

        if (recentAnomalies.length > 0) {
          const avgSeverity = recentAnomalies.reduce((sum, a) => {
            const severityScore = { low: 1, medium: 2, high: 3, critical: 4 };
            return sum + severityScore[a.severity];
          }, 0) / recentAnomalies.length;

          insights.push({
            id: `insight_recent_anomalies_${series.id}_${Date.now()}`,
            type: 'anomaly',
            title: `Recent Anomaly Activity in ${series.name}`,
            description: `${recentAnomalies.length} anomal${recentAnomalies.length > 1 ? 'ies' : 'y'} detected in the last ${timeWindow} days. Average severity: ${this.getSeverityLabel(avgSeverity)}.`,
            severity: avgSeverity > 2.5 ? 'warning' : 'info',
            confidence: 0.85,
            timestamp: new Date().toISOString(),
            data: {
              seriesId: series.id,
              anomalies: recentAnomalies,
              averageSeverity: avgSeverity,
            },
          });
        }

        // Detect anomaly patterns
        if (anomalies.length >= 3) {
          const anomalyPatterns = this.detectAnomalyPatterns(anomalies);
          if (anomalyPatterns.length > 0) {
            insights.push({
              id: `insight_anomaly_patterns_${series.id}_${Date.now()}`,
              type: 'anomaly',
              title: `Recurring Anomaly Patterns in ${series.name}`,
              description: `Detected recurring anomaly patterns that may indicate systematic issues.`,
              severity: 'warning',
              confidence: 0.7,
              timestamp: new Date().toISOString(),
              data: {
                seriesId: series.id,
                patterns: anomalyPatterns,
              },
            });
          }
        }
      }
    } catch (error) {
      console.error('Failed to generate anomaly insights:', error);
    }

    return insights;
  }

  // Generate forecast-based insights
  private async generateForecastInsights(series: TimeSeries): Promise<Insight[]> {
    const insights: Insight[] = [];

    try {
      const forecast = await mlService.forecastTimeSeries(series, { horizon: 7 });
      const currentValue = series.data[series.data.length - 1]?.value || 0;
      const forecastTrend = this.analyzeForecastTrend(forecast.predictions);

      if (forecastTrend.direction !== 'stable') {
        const changePercent = Math.abs(forecastTrend.changePercent);
        let severity: 'info' | 'warning' | 'critical' = 'info';

        if (changePercent > 30) severity = 'critical';
        else if (changePercent > 15) severity = 'warning';

        insights.push({
          id: `insight_forecast_trend_${series.id}_${Date.now()}`,
          type: 'trend',
          title: `${series.name} ${forecastTrend.direction === 'increasing' ? 'Expected to Increase' : 'Expected to Decrease'}`,
          description: `Forecast shows a ${changePercent.toFixed(1)}% ${forecastTrend.direction === 'increasing' ? 'increase' : 'decrease'} over the next 7 days.`,
          severity,
          confidence: forecast.confidence,
          timestamp: new Date().toISOString(),
          data: {
            seriesId: series.id,
            forecast: forecast.predictions,
            currentValue,
            trend: forecastTrend,
          },
          actions: [
            {
              id: 'view_forecast',
              label: 'View Detailed Forecast',
              type: 'link',
              url: `/analytics/forecast?series=${series.id}`,
            },
          ],
        });
      }

      // Check for forecast confidence issues
      if (forecast.confidence < 0.5) {
        insights.push({
          id: `insight_forecast_uncertainty_${series.id}_${Date.now()}`,
          type: 'recommendation',
          title: `Low Forecast Confidence for ${series.name}`,
          description: `Forecast confidence is ${(forecast.confidence * 100).toFixed(1)}%. Consider collecting more data or reviewing data quality.`,
          severity: 'warning',
          confidence: 0.8,
          timestamp: new Date().toISOString(),
          data: {
            seriesId: series.id,
            forecastConfidence: forecast.confidence,
          },
          actions: [
            {
              id: 'check_data_quality',
              label: 'Check Data Quality',
              type: 'action',
              action: 'analyze_data_quality',
              parameters: { seriesId: series.id },
            },
          ],
        });
      }
    } catch (error) {
      console.error('Failed to generate forecast insights:', error);
    }

    return insights;
  }

  // Generate pattern-based insights
  private async generatePatternInsights(series: TimeSeries): Promise<Insight[]> {
    const insights: Insight[] = [];

    try {
      const patternAnalysis = await mlService.analyzePatterns(series);

      for (const pattern of patternAnalysis.patterns) {
        let title = '';
        let description = '';
        let severity: 'info' | 'warning' | 'critical' = 'info';

        switch (pattern.type) {
          case 'trend':
            title = `${pattern.direction === 'increasing' ? 'Upward' : 'Downward'} Trend in ${series.name}`;
            description = `Strong ${pattern.direction} trend detected with ${(pattern.strength * 100).toFixed(1)}% confidence.`;
            severity = pattern.strength > 0.7 ? 'warning' : 'info';
            break;

          case 'seasonal':
            title = `Seasonal Pattern in ${series.name}`;
            description = `Recurring seasonal pattern detected with ${pattern.period}-period cycles.`;
            severity = 'info';
            break;

          case 'cyclical':
            title = `Cyclical Behavior in ${series.name}`;
            description = `Cyclical pattern detected with approximately ${pattern.period}-period cycles.`;
            severity = 'info';
            break;
        }

        insights.push({
          id: `insight_pattern_${pattern.type}_${series.id}_${Date.now()}`,
          type: 'trend',
          title,
          description,
          severity,
          confidence: pattern.confidence,
          timestamp: new Date().toISOString(),
          data: {
            seriesId: series.id,
            pattern,
          },
        });
      }
    } catch (error) {
      console.error('Failed to generate pattern insights:', error);
    }

    return insights;
  }

  // Generate correlation insights between multiple series
  private async generateCorrelationInsights(series: TimeSeries[]): Promise<Insight[]> {
    const insights: Insight[] = [];

    try {
      for (let i = 0; i < series.length; i++) {
        for (let j = i + 1; j < series.length; j++) {
          const correlation = await mlService.analyzeCorrelation(series[i], series[j]);

          if (correlation.significance !== 'not_significant') {
            let severity: 'info' | 'warning' | 'critical' = 'info';
            if (correlation.significance === 'very_strong') severity = 'warning';
            if (Math.abs(correlation.coefficient) > 0.8) severity = 'critical';

            insights.push({
              id: `insight_correlation_${series[i].id}_${series[j].id}_${Date.now()}`,
              type: 'correlation',
              title: `${correlation.significance.replace('_', ' ').toUpperCase()} Correlation Found`,
              description: `${series[i].name} and ${series[j].name} show ${correlation.significance.replace('_', ' ')} correlation (${(correlation.coefficient * 100).toFixed(1)}%).`,
              severity,
              confidence: 1 - correlation.pValue,
              timestamp: new Date().toISOString(),
              data: {
                series1Id: series[i].id,
                series2Id: series[j].id,
                correlation,
              },
              actions: [
                {
                  id: 'view_correlation',
                  label: 'View Correlation Analysis',
                  type: 'link',
                  url: `/analytics/correlation?series1=${series[i].id}&series2=${series[j].id}`,
                },
              ],
            });
          }
        }
      }
    } catch (error) {
      console.error('Failed to generate correlation insights:', error);
    }

    return insights;
  }

  // Generate performance insights
  private async generatePerformanceInsights(series: TimeSeries[]): Promise<Insight[]> {
    const insights: Insight[] = [];

    try {
      for (const serie of series) {
        const values = serie.data.map(d => d.value);
        const recentValues = values.slice(-7); // Last 7 data points
        const previousValues = values.slice(-14, -7); // Previous 7 data points

        if (recentValues.length >= 7 && previousValues.length >= 7) {
          const recentAvg = recentValues.reduce((sum, val) => sum + val, 0) / recentValues.length;
          const previousAvg = previousValues.reduce((sum, val) => sum + val, 0) / previousValues.length;
          const changePercent = ((recentAvg - previousAvg) / previousAvg) * 100;

          if (Math.abs(changePercent) > 10) {
            const isImprovement = this.determineIfImprovement(serie.name, changePercent);

            insights.push({
              id: `insight_performance_${serie.id}_${Date.now()}`,
              type: 'trend',
              title: `${serie.name} Performance ${isImprovement ? 'Improvement' : 'Decline'}`,
              description: `${Math.abs(changePercent).toFixed(1)}% ${changePercent > 0 ? 'increase' : 'decrease'} in average values compared to the previous period.`,
              severity: isImprovement ? 'info' : 'warning',
              confidence: 0.8,
              timestamp: new Date().toISOString(),
              data: {
                seriesId: serie.id,
                changePercent,
                recentAvg,
                previousAvg,
              },
            });
          }
        }
      }
    } catch (error) {
      console.error('Failed to generate performance insights:', error);
    }

    return insights;
  }

  // Generate optimization recommendations
  private async generateOptimizationInsights(series: TimeSeries[]): Promise<Insight[]> {
    const insights: Insight[] = [];

    try {
      for (const serie of series) {
        // Check data quality
        const dataQuality = this.assessDataQuality(serie);
        if (dataQuality.score < 0.7) {
          insights.push({
            id: `insight_data_quality_${serie.id}_${Date.now()}`,
            type: 'recommendation',
            title: `Data Quality Issues in ${serie.name}`,
            description: `Data quality score: ${(dataQuality.score * 100).toFixed(1)}%. ${dataQuality.issues.join(', ')}.`,
            severity: 'warning',
            confidence: 0.9,
            timestamp: new Date().toISOString(),
            data: {
              seriesId: serie.id,
              qualityScore: dataQuality.score,
              issues: dataQuality.issues,
            },
            actions: [
              {
                id: 'improve_data_quality',
                label: 'View Data Quality Report',
                type: 'action',
                action: 'show_data_quality_report',
                parameters: { seriesId: serie.id },
              },
            ],
          });
        }

        // Check for monitoring gaps
        const monitoringGaps = this.detectMonitoringGaps(serie);
        if (monitoringGaps.length > 0) {
          insights.push({
            id: `insight_monitoring_gaps_${serie.id}_${Date.now()}`,
            type: 'recommendation',
            title: `Monitoring Gaps in ${serie.name}`,
            description: `${monitoringGaps.length} monitoring gap${monitoringGaps.length > 1 ? 's' : ''} detected. Consider improving data collection frequency.`,
            severity: 'info',
            confidence: 0.8,
            timestamp: new Date().toISOString(),
            data: {
              seriesId: serie.id,
              gaps: monitoringGaps,
            },
          });
        }
      }
    } catch (error) {
      console.error('Failed to generate optimization insights:', error);
    }

    return insights;
  }

  // Helper methods
  private getSeverityLabel(score: number): string {
    if (score >= 3.5) return 'very high';
    if (score >= 2.5) return 'high';
    if (score >= 1.5) return 'medium';
    return 'low';
  }

  private detectAnomalyPatterns(anomalies: Anomaly[]): any[] {
    // Simplified pattern detection
    const patterns: any[] = [];

    // Group by hour of day
    const hourGroups: Record<number, Anomaly[]> = {};
    anomalies.forEach(anomaly => {
      const hour = new Date(anomaly.timestamp).getHours();
      if (!hourGroups[hour]) hourGroups[hour] = [];
      hourGroups[hour].push(anomaly);
    });

    // Find hours with multiple anomalies
    Object.entries(hourGroups).forEach(([hour, hourAnomalies]) => {
      if (hourAnomalies.length >= 2) {
        patterns.push({
          type: 'time_based',
          description: `${hourAnomalies.length} anomalies at hour ${hour}`,
          count: hourAnomalies.length,
          hour: parseInt(hour),
        });
      }
    });

    return patterns;
  }

  private analyzeForecastTrend(predictions: { value: number }[]): {
    direction: 'increasing' | 'decreasing' | 'stable';
    changePercent: number;
  } {
    if (predictions.length < 2) {
      return { direction: 'stable', changePercent: 0 };
    }

    const firstValue = predictions[0].value;
    const lastValue = predictions[predictions.length - 1].value;
    const changePercent = ((lastValue - firstValue) / firstValue) * 100;

    let direction: 'increasing' | 'decreasing' | 'stable' = 'stable';
    if (Math.abs(changePercent) > 5) {
      direction = changePercent > 0 ? 'increasing' : 'decreasing';
    }

    return { direction, changePercent: Math.abs(changePercent) };
  }

  private determineIfImprovement(seriesName: string, changePercent: number): boolean {
    // Simple heuristic based on series name
    const positiveIndicators = ['revenue', 'sales', 'profit', 'users', 'growth', 'performance'];
    const negativeIndicators = ['errors', 'failures', 'costs', 'latency', 'downtime'];

    const nameLower = seriesName.toLowerCase();

    for (const indicator of positiveIndicators) {
      if (nameLower.includes(indicator)) {
        return changePercent > 0;
      }
    }

    for (const indicator of negativeIndicators) {
      if (nameLower.includes(indicator)) {
        return changePercent < 0;
      }
    }

    // Default: assume increase is improvement
    return changePercent > 0;
  }

  private assessDataQuality(series: TimeSeries): { score: number; issues: string[] } {
    const issues: string[] = [];
    let score = 1.0;

    // Check for missing values
    const missingCount = series.data.filter(d => d.value === null || d.value === undefined).length;
    const missingPercent = missingCount / series.data.length;

    if (missingPercent > 0.1) {
      issues.push(`${(missingPercent * 100).toFixed(1)}% missing values`);
      score -= missingPercent * 0.5;
    }

    // Check for outliers
    const values = series.data.map(d => d.value).filter(v => v !== null && v !== undefined);
    const mean = values.reduce((sum, val) => sum + val, 0) / values.length;
    const std = Math.sqrt(values.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / values.length);

    const outliers = values.filter(v => Math.abs(v - mean) > 3 * std);
    const outlierPercent = outliers.length / values.length;

    if (outlierPercent > 0.05) {
      issues.push(`${(outlierPercent * 100).toFixed(1)}% outliers`);
      score -= outlierPercent * 0.3;
    }

    // Check for data staleness
    const lastDataPoint = series.data[series.data.length - 1];
    if (lastDataPoint) {
      const hoursSinceLastUpdate = (Date.now() - new Date(lastDataPoint.timestamp).getTime()) / (1000 * 60 * 60);
      if (hoursSinceLastUpdate > 24) {
        issues.push(`Data is ${Math.floor(hoursSinceLastUpdate)} hours old`);
        score -= 0.2;
      }
    }

    return { score: Math.max(0, score), issues };
  }

  private detectMonitoringGaps(series: TimeSeries): Array<{ start: string; end: string; duration: number }> {
    const gaps: Array<{ start: string; end: string; duration: number }> = [];

    if (series.data.length < 2) return gaps;

    // Calculate expected interval
    const intervals: number[] = [];
    for (let i = 1; i < Math.min(series.data.length, 10); i++) {
      const current = new Date(series.data[i].timestamp).getTime();
      const previous = new Date(series.data[i - 1].timestamp).getTime();
      intervals.push(current - previous);
    }

    if (intervals.length === 0) return gaps;

    intervals.sort((a, b) => a - b);
    const medianInterval = intervals[Math.floor(intervals.length / 2)];
    const gapThreshold = medianInterval * 3; // 3x the normal interval

    // Find gaps
    for (let i = 1; i < series.data.length; i++) {
      const current = new Date(series.data[i].timestamp).getTime();
      const previous = new Date(series.data[i - 1].timestamp).getTime();
      const interval = current - previous;

      if (interval > gapThreshold) {
        gaps.push({
          start: series.data[i - 1].timestamp.toString(),
          end: series.data[i].timestamp.toString(),
          duration: interval,
        });
      }
    }

    return gaps;
  }
}

export const insightsEngine = InsightsEngine.getInstance();