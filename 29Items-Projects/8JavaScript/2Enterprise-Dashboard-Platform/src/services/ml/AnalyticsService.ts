import {
  TimeSeries,
  Anomaly,
  Forecast,
  Insight,
  PatternAnalysis,
  CorrelationAnalysis,
  Alert,
  AlertRule,
  AnalyticsReport,
  MLModel,
  DataPoint,
  MLError,
  TimeRange,
} from './types';
import { mlService } from './MLService';
import { insightsEngine } from './InsightsEngine';

export class AnalyticsService {
  private static instance: AnalyticsService;
  private alertRules: AlertRule[] = [];
  private alerts: Alert[] = [];
  private models: MLModel[] = [];

  private constructor() {}

  static getInstance(): AnalyticsService {
    if (!AnalyticsService.instance) {
      AnalyticsService.instance = new AnalyticsService();
    }
    return AnalyticsService.instance;
  }

  // Initialize the analytics service
  async initialize(): Promise<void> {
    try {
      await mlService.initialize();
      console.log('Analytics service initialized successfully');
    } catch (error) {
      console.error('Failed to initialize analytics service:', error);
      throw error;
    }
  }

  // Comprehensive analytics for dashboard widgets
  async analyzeDashboardData(
    series: TimeSeries[],
    options: {
      includeAnomalies?: boolean;
      includeForecasts?: boolean;
      includeInsights?: boolean;
      includePatterns?: boolean;
      includeCorrelations?: boolean;
      timeWindow?: number;
    } = {}
  ): Promise<{
    anomalies: Record<string, Anomaly[]>;
    forecasts: Record<string, Forecast>;
    insights: Insight[];
    patterns: Record<string, PatternAnalysis>;
    correlations: CorrelationAnalysis[];
    summary: AnalyticsSummary;
  }> {
    const {
      includeAnomalies = true,
      includeForecasts = true,
      includeInsights = true,
      includePatterns = true,
      includeCorrelations = true,
      timeWindow = 30,
    } = options;

    const results = {
      anomalies: {} as Record<string, Anomaly[]>,
      forecasts: {} as Record<string, Forecast>,
      insights: [] as Insight[],
      patterns: {} as Record<string, PatternAnalysis>,
      correlations: [] as CorrelationAnalysis[],
      summary: {} as AnalyticsSummary,
    };

    try {
      // Process each series
      for (const serie of series) {
        if (includeAnomalies) {
          try {
            results.anomalies[serie.id] = await mlService.detectAnomalies(serie);
          } catch (error) {
            console.warn(`Failed to detect anomalies for series ${serie.id}:`, error);
            results.anomalies[serie.id] = [];
          }
        }

        if (includeForecasts) {
          try {
            results.forecasts[serie.id] = await mlService.forecastTimeSeries(serie);
          } catch (error) {
            console.warn(`Failed to generate forecast for series ${serie.id}:`, error);
          }
        }

        if (includePatterns) {
          try {
            results.patterns[serie.id] = await mlService.analyzePatterns(serie);
          } catch (error) {
            console.warn(`Failed to analyze patterns for series ${serie.id}:`, error);
          }
        }
      }

      // Process correlations between series
      if (includeCorrelations && series.length > 1) {
        for (let i = 0; i < series.length; i++) {
          for (let j = i + 1; j < series.length; j++) {
            try {
              const correlation = await mlService.analyzeCorrelation(series[i], series[j]);
              if (correlation.significance !== 'not_significant') {
                results.correlations.push(correlation);
              }
            } catch (error) {
              console.warn(`Failed to analyze correlation between ${series[i].id} and ${series[j].id}:`, error);
            }
          }
        }
      }

      // Generate insights
      if (includeInsights) {
        try {
          results.insights = await insightsEngine.generateInsights(series, {
            includeAnomalies,
            includeForecasts,
            includePatterns,
            includeCorrelations,
            timeWindow,
          });
        } catch (error) {
          console.warn('Failed to generate insights:', error);
          results.insights = [];
        }
      }

      // Generate summary
      results.summary = this.generateAnalyticsSummary(results, series);

      return results;
    } catch (error) {
      console.error('Analytics processing failed:', error);
      throw new MLError({
        code: 'ANALYTICS_FAILED',
        message: 'Failed to process analytics',
        details: error,
        timestamp: new Date().toISOString(),
      });
    }
  }

  // Real-time anomaly monitoring
  async monitorAnomalies(
    series: TimeSeries,
    options: {
      sensitivity?: 'low' | 'medium' | 'high';
      alertRuleId?: string;
    } = {}
  ): Promise<{
    anomalies: Anomaly[];
    alerts: Alert[];
    shouldAlert: boolean;
  }> {
    const { sensitivity = 'medium', alertRuleId } = options;

    try {
      const anomalies = await mlService.detectAnomalies(series, { sensitivity });
      const criticalAnomalies = anomalies.filter(a => a.severity === 'critical' || a.severity === 'high');

      let alerts: Alert[] = [];
      let shouldAlert = false;

      if (criticalAnomalies.length > 0 && alertRuleId) {
        const rule = this.alertRules.find(r => r.id === alertRuleId);
        if (rule && rule.isActive) {
          alerts = await this.generateAlerts(criticalAnomalies, rule);
          shouldAlert = alerts.length > 0;
        }
      }

      return {
        anomalies,
        alerts,
        shouldAlert,
      };
    } catch (error) {
      console.error('Anomaly monitoring failed:', error);
      throw error;
    }
  }

  // Predictive analytics with confidence scoring
  async generatePredictions(
    series: TimeSeries,
    options: {
      horizon?: number;
      includeConfidenceIntervals?: boolean;
      seasonal?: boolean;
    } = {}
  ): Promise<{
    forecast: Forecast;
    accuracy: number;
    recommendations: string[];
  }> {
    const { horizon = 7, includeConfidenceIntervals = true, seasonal = false } = options;

    try {
      const forecast = await mlService.forecastTimeSeries(series, {
        horizon,
        seasonal,
      });

      // Calculate accuracy based on historical performance (simplified)
      const accuracy = this.calculateForecastAccuracy(series, forecast);

      // Generate recommendations
      const recommendations = this.generateForecastRecommendations(forecast, accuracy);

      return {
        forecast,
        accuracy,
        recommendations,
      };
    } catch (error) {
      console.error('Prediction generation failed:', error);
      throw error;
    }
  }

  // Pattern recognition and trend analysis
  async performPatternAnalysis(
    series: TimeSeries[],
    options: {
      detectSeasonality?: boolean;
      detectTrends?: boolean;
      detectCycles?: boolean;
      minConfidence?: number;
    } = {}
  ): Promise<{
    patterns: Record<string, PatternAnalysis>;
    insights: Insight[];
    recommendations: string[];
  }> {
    const {
      detectSeasonality = true,
      detectTrends = true,
      detectCycles = true,
      minConfidence = 0.6,
    } = options;

    const results = {
      patterns: {} as Record<string, PatternAnalysis>,
      insights: [] as Insight[],
      recommendations: [] as string[],
    };

    try {
      for (const serie of series) {
        const analysis = await mlService.analyzePatterns(serie);

        // Filter patterns by confidence
        analysis.patterns = analysis.patterns.filter(p => p.confidence >= minConfidence);

        results.patterns[serie.id] = analysis;

        // Generate pattern-based insights
        for (const pattern of analysis.patterns) {
          results.insights.push(this.createPatternInsight(serie, pattern));
        }
      }

      // Generate pattern-based recommendations
      results.recommendations = this.generatePatternRecommendations(results.patterns);

      return results;
    } catch (error) {
      console.error('Pattern analysis failed:', error);
      throw error;
    }
  }

  // Smart alerting system
  async createAlertRule(rule: Omit<AlertRule, 'id'>): Promise<AlertRule> {
    const newRule: AlertRule = {
      ...rule,
      id: `alert_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    };

    this.alertRules.push(newRule);
    return newRule;
  }

  async updateAlertRule(ruleId: string, updates: Partial<AlertRule>): Promise<AlertRule | null> {
    const ruleIndex = this.alertRules.findIndex(r => r.id === ruleId);
    if (ruleIndex === -1) return null;

    this.alertRules[ruleIndex] = { ...this.alertRules[ruleIndex], ...updates };
    return this.alertRules[ruleIndex];
  }

  async deleteAlertRule(ruleId: string): Promise<boolean> {
    const ruleIndex = this.alertRules.findIndex(r => r.id === ruleId);
    if (ruleIndex === -1) return false;

    this.alertRules.splice(ruleIndex, 1);
    return true;
  }

  getAlertRules(): AlertRule[] {
    return [...this.alertRules];
  }

  getAlerts(options: { isResolved?: boolean; limit?: number } = {}): Alert[] {
    let filtered = [...this.alerts];

    if (options.isResolved !== undefined) {
      filtered = filtered.filter(alert => alert.isResolved === options.isResolved);
    }

    if (options.limit) {
      filtered = filtered.slice(0, options.limit);
    }

    return filtered.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
  }

  // Automated report generation
  async generateAnalyticsReport(
    series: TimeSeries[],
    options: {
      type?: 'summary' | 'detailed' | 'comparison' | 'forecast';
      period?: TimeRange;
      includeCharts?: boolean;
      includeTables?: boolean;
    } = {}
  ): Promise<AnalyticsReport> {
    const {
      type = 'summary',
      period = {
        start: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
        end: new Date().toISOString(),
      },
      includeCharts = true,
      includeTables = true,
    } = options;

    try {
      const analytics = await this.analyzeDashboardData(series);
      const insights = analytics.insights;

      const report: AnalyticsReport = {
        id: `report_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        title: `Analytics Report - ${type.charAt(0).toUpperCase() + type.slice(1)}`,
        description: `Generated analytics report for ${series.length} data series`,
        type,
        seriesIds: series.map(s => s.id),
        period,
        insights,
        charts: includeCharts ? this.generateReportCharts(series, analytics) : [],
        tables: includeTables ? this.generateReportTables(series, analytics) : [],
        createdAt: new Date().toISOString(),
        createdBy: 'system',
        isAutoGenerated: true,
      };

      return report;
    } catch (error) {
      console.error('Report generation failed:', error);
      throw error;
    }
  }

  // Data quality assessment
  async assessDataQuality(series: TimeSeries[]): Promise<{
    overallScore: number;
    seriesScores: Record<string, number>;
    issues: Array<{
      seriesId: string;
      type: string;
      severity: 'low' | 'medium' | 'high';
      description: string;
    }>;
    recommendations: string[];
  }> {
    const seriesScores: Record<string, number> = {};
    const issues: Array<{
      seriesId: string;
      type: string;
      severity: 'low' | 'medium' | 'high';
      description: string;
    }> = [];

    for (const serie of series) {
      const assessment = this.assessSeriesQuality(serie);
      seriesScores[serie.id] = assessment.score;
      issues.push(...assessment.issues.map(issue => ({
        seriesId: serie.id,
        ...issue,
      })));
    }

    const overallScore = Object.values(seriesScores).reduce((sum, score) => sum + score, 0) / series.length;

    return {
      overallScore,
      seriesScores,
      issues,
      recommendations: this.generateDataQualityRecommendations(issues),
    };
  }

  // Helper methods
  private generateAnalyticsSummary(
    results: any,
    series: TimeSeries[]
  ): AnalyticsSummary {
    const totalAnomalies = Object.values(results.anomalies as Record<string, Anomaly[]>)
      .reduce((sum, anomalies) => sum + anomalies.length, 0);

    const criticalInsights = results.insights.filter((insight: Insight) => insight.severity === 'critical').length;

    const avgForecastConfidence = Object.values(results.forecasts as Record<string, Forecast>)
      .reduce((sum, forecast) => sum + forecast.confidence, 0) / Math.max(1, Object.keys(results.forecasts).length);

    return {
      totalSeries: series.length,
      totalAnomalies,
      totalInsights: results.insights.length,
      criticalInsights,
      avgForecastConfidence,
      hasStrongCorrelations: results.correlations.some((c: CorrelationAnalysis) =>
        c.significance === 'strong' || c.significance === 'very_strong'
      ),
      dataQualityScore: 0.8, // Would be calculated from actual assessment
      lastUpdated: new Date().toISOString(),
    };
  }

  private async generateAlerts(anomalies: Anomaly[], rule: AlertRule): Promise<Alert[]> {
    const alerts: Alert[] = [];

    for (const anomaly of anomalies) {
      const alert: Alert = {
        id: `alert_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        ruleId: rule.id,
        timestamp: new Date().toISOString(),
        severity: anomaly.severity === 'critical' ? 'critical' : 'warning',
        title: `Anomaly Alert: ${rule.name}`,
        description: anomaly.description,
        value: anomaly.value,
        isResolved: false,
        metadata: {
          anomalyId: anomaly.id,
          expectedValue: anomaly.expectedValue,
          score: anomaly.score,
        },
      };

      alerts.push(alert);
      this.alerts.push(alert);
    }

    return alerts;
  }

  private calculateForecastAccuracy(series: TimeSeries, forecast: Forecast): number {
    // Simplified accuracy calculation
    // In a real implementation, this would use historical forecast performance
    return Math.max(0.5, forecast.confidence * 0.9 + Math.random() * 0.1);
  }

  private generateForecastRecommendations(forecast: Forecast, accuracy: number): string[] {
    const recommendations: string[] = [];

    if (forecast.confidence < 0.6) {
      recommendations.push('Consider collecting more historical data to improve forecast accuracy');
    }

    if (accuracy < 0.7) {
      recommendations.push('Review data quality and consider alternative forecasting models');
    }

    const trend = this.analyzeForecastTrend(forecast.predictions);
    if (trend.direction !== 'stable') {
      recommendations.push(`Monitor closely: forecast shows ${trend.direction} trend of ${trend.changePercent.toFixed(1)}%`);
    }

    return recommendations;
  }

  private analyzeForecastTrend(predictions: DataPoint[]): {
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

  private createPatternInsight(series: TimeSeries, pattern: any): Insight {
    return {
      id: `pattern_insight_${series.id}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      type: 'trend',
      title: `Pattern Detected in ${series.name}`,
      description: pattern.description,
      severity: pattern.strength > 0.7 ? 'warning' : 'info',
      confidence: pattern.confidence,
      timestamp: new Date().toISOString(),
      data: {
        seriesId: series.id,
        pattern,
      },
    };
  }

  private generatePatternRecommendations(patterns: Record<string, PatternAnalysis>): string[] {
    const recommendations: string[] = [];

    Object.values(patterns).forEach(analysis => {
      const strongPatterns = analysis.patterns.filter(p => p.strength > 0.7);
      if (strongPatterns.length > 0) {
        recommendations.push(`Strong patterns detected - consider seasonal adjustments or trend-based planning`);
      }
    });

    return recommendations;
  }

  private assessSeriesQuality(series: TimeSeries): {
    score: number;
    issues: Array<{
      type: string;
      severity: 'low' | 'medium' | 'high';
      description: string;
    }>;
  } {
    const issues: Array<{
      type: string;
      severity: 'low' | 'medium' | 'high';
      description: string;
    }> = [];
    let score = 1.0;

    // Check for missing values
    const missingCount = series.data.filter(d => d.value === null || d.value === undefined).length;
    const missingPercent = missingCount / series.data.length;

    if (missingPercent > 0.1) {
      issues.push({
        type: 'missing_data',
        severity: missingPercent > 0.3 ? 'high' : 'medium',
        description: `${(missingPercent * 100).toFixed(1)}% missing values`,
      });
      score -= missingPercent * 0.5;
    }

    // Check data recency
    if (series.data.length > 0) {
      const lastUpdate = new Date(series.data[series.data.length - 1].timestamp);
      const hoursSinceUpdate = (Date.now() - lastUpdate.getTime()) / (1000 * 60 * 60);

      if (hoursSinceUpdate > 24) {
        issues.push({
          type: 'stale_data',
          severity: hoursSinceUpdate > 72 ? 'high' : 'medium',
          description: `Data is ${Math.floor(hoursSinceUpdate)} hours old`,
        });
        score -= 0.2;
      }
    }

    return { score: Math.max(0, score), issues };
  }

  private generateDataQualityRecommendations(issues: Array<{ type: string; severity: string; description: string }>): string[] {
    const recommendations: string[] = [];

    const hasHighSeverityIssues = issues.some(issue => issue.severity === 'high');
    const hasMissingData = issues.some(issue => issue.type === 'missing_data');
    const hasStaleData = issues.some(issue => issue.type === 'stale_data');

    if (hasHighSeverityIssues) {
      recommendations.push('Address high-severity data quality issues immediately');
    }

    if (hasMissingData) {
      recommendations.push('Implement data validation and error handling in data collection');
    }

    if (hasStaleData) {
      recommendations.push('Review data pipeline and ensure timely updates');
    }

    return recommendations;
  }

  private generateReportCharts(series: TimeSeries[], analytics: any): any[] {
    // Simplified chart generation for reports
    return [
      {
        id: 'trends_overview',
        type: 'line',
        title: 'Trends Overview',
        data: series.map(s => ({
          name: s.name,
          data: s.data.slice(-30).map(d => ({ x: d.timestamp, y: d.value })),
        })),
        config: {},
      },
    ];
  }

  private generateReportTables(series: TimeSeries[], analytics: any): any[] {
    // Simplified table generation for reports
    return [
      {
        id: 'series_summary',
        title: 'Data Series Summary',
        columns: [
          { key: 'name', title: 'Series Name', type: 'string' },
          { key: 'dataPoints', title: 'Data Points', type: 'number' },
          { key: 'anomalies', title: 'Anomalies', type: 'number' },
          { key: 'lastUpdate', title: 'Last Update', type: 'date' },
        ],
        data: series.map(s => ({
          name: s.name,
          dataPoints: s.data.length,
          anomalies: analytics.anomalies[s.id]?.length || 0,
          lastUpdate: s.data[s.data.length - 1]?.timestamp || 'N/A',
        })),
      },
    ];
  }
}

// Types for analytics summary and related structures
interface AnalyticsSummary {
  totalSeries: number;
  totalAnomalies: number;
  totalInsights: number;
  criticalInsights: number;
  avgForecastConfidence: number;
  hasStrongCorrelations: boolean;
  dataQualityScore: number;
  lastUpdated: string;
}

export const analyticsService = AnalyticsService.getInstance();