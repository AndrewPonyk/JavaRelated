import * as tf from '@tensorflow/tfjs';
import {
  DataPoint,
  TimeSeries,
  Anomaly,
  Forecast,
  PatternAnalysis,
  DetectedPattern,
  CorrelationAnalysis,
  MLError,
  TimeRange,
  AggregationType
} from './types';

export class MLService {
  private static instance: MLService;
  private isInitialized = false;

  private constructor() {}

  static getInstance(): MLService {
    if (!MLService.instance) {
      MLService.instance = new MLService();
    }
    return MLService.instance;
  }

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      // Initialize TensorFlow.js
      await tf.ready();
      console.log('TensorFlow.js initialized');
      console.log('Backend:', tf.getBackend());
      this.isInitialized = true;
    } catch (error) {
      console.error('Failed to initialize TensorFlow.js:', error);
      throw new MLError({
        code: 'INIT_FAILED',
        message: 'Failed to initialize ML service',
        details: error,
        timestamp: new Date().toISOString(),
      });
    }
  }

  // Data preprocessing utilities
  preprocessTimeSeries(series: TimeSeries): {
    normalizedData: number[];
    mean: number;
    std: number;
    originalData: DataPoint[];
  } {
    const values = series.data.map(d => d.value);
    const mean = values.reduce((sum, val) => sum + val, 0) / values.length;
    const std = Math.sqrt(
      values.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / values.length
    );

    const normalizedData = values.map(val => (val - mean) / (std || 1));

    return {
      normalizedData,
      mean,
      std,
      originalData: series.data,
    };
  }

  denormalizeValue(normalizedValue: number, mean: number, std: number): number {
    return normalizedValue * std + mean;
  }

  // Anomaly Detection using Isolation Forest-like approach
  async detectAnomalies(
    series: TimeSeries,
    options: {
      sensitivity?: 'low' | 'medium' | 'high';
      windowSize?: number;
      contamination?: number;
    } = {}
  ): Promise<Anomaly[]> {
    if (!this.isInitialized) await this.initialize();

    const { sensitivity = 'medium', windowSize = 20, contamination = 0.1 } = options;
    const { normalizedData, mean, std, originalData } = this.preprocessTimeSeries(series);

    if (normalizedData.length < windowSize) {
      throw new MLError({
        code: 'INSUFFICIENT_DATA',
        message: 'Not enough data points for anomaly detection',
        timestamp: new Date().toISOString(),
      });
    }

    const anomalies: Anomaly[] = [];
    const sensitivityThresholds = {
      low: 3.0,
      medium: 2.5,
      high: 2.0,
    };
    const threshold = sensitivityThresholds[sensitivity];

    // Rolling window anomaly detection
    for (let i = windowSize; i < normalizedData.length; i++) {
      const window = normalizedData.slice(i - windowSize, i);
      const currentValue = normalizedData[i];

      const windowMean = window.reduce((sum, val) => sum + val, 0) / window.length;
      const windowStd = Math.sqrt(
        window.reduce((sum, val) => sum + Math.pow(val - windowMean, 2), 0) / window.length
      );

      const zScore = Math.abs((currentValue - windowMean) / (windowStd || 1));

      if (zScore > threshold) {
        const originalValue = originalData[i].value;
        const expectedValue = this.denormalizeValue(windowMean, mean, std);

        anomalies.push({
          id: `anomaly_${series.id}_${i}_${Date.now()}`,
          timestamp: originalData[i].timestamp,
          value: originalValue,
          expectedValue,
          severity: this.calculateSeverity(zScore, threshold),
          score: Math.min(zScore / 4, 1), // Normalize to 0-1
          type: this.classifyAnomalyType(originalValue, expectedValue, window.map(v => this.denormalizeValue(v, mean, std))),
          description: `Anomalous value detected: ${originalValue.toFixed(2)} (expected: ${expectedValue.toFixed(2)})`,
          metadata: {
            zScore,
            windowSize,
            threshold,
          },
        });
      }
    }

    return anomalies;
  }

  // Time Series Forecasting using Simple Exponential Smoothing
  async forecastTimeSeries(
    series: TimeSeries,
    options: {
      horizon?: number;
      alpha?: number;
      beta?: number;
      gamma?: number;
      seasonal?: boolean;
      seasonLength?: number;
    } = {}
  ): Promise<Forecast> {
    if (!this.isInitialized) await this.initialize();

    const {
      horizon = 10,
      alpha = 0.3,
      beta = 0.1,
      gamma = 0.1,
      seasonal = false,
      seasonLength = 7
    } = options;

    const values = series.data.map(d => d.value);

    if (values.length < (seasonal ? seasonLength * 2 : 3)) {
      throw new MLError({
        code: 'INSUFFICIENT_DATA',
        message: 'Not enough data points for forecasting',
        timestamp: new Date().toISOString(),
      });
    }

    let forecast: number[];
    let confidence = 0.7; // Base confidence

    if (seasonal && values.length >= seasonLength * 2) {
      forecast = this.holtsWintersForecasting(values, alpha, beta, gamma, seasonLength, horizon);
      confidence = 0.8;
    } else {
      forecast = this.exponentialSmoothing(values, alpha, horizon);
      confidence = 0.6;
    }

    // Generate timestamps for forecast
    const lastTimestamp = new Date(series.data[series.data.length - 1].timestamp);
    const intervalMs = this.estimateInterval(series.data);

    const predictions: DataPoint[] = forecast.map((value, index) => ({
      timestamp: new Date(lastTimestamp.getTime() + (index + 1) * intervalMs).toISOString(),
      value: Math.max(0, value), // Ensure non-negative values
    }));

    return {
      id: `forecast_${series.id}_${Date.now()}`,
      seriesId: series.id,
      predictions,
      confidence,
      horizon,
      model: seasonal ? 'holt_winters' : 'exponential_smoothing',
      createdAt: new Date().toISOString(),
      metadata: {
        alpha,
        beta: seasonal ? beta : undefined,
        gamma: seasonal ? gamma : undefined,
        seasonal,
        seasonLength: seasonal ? seasonLength : undefined,
      },
    };
  }

  // Pattern Analysis
  async analyzePatterns(series: TimeSeries): Promise<PatternAnalysis> {
    const patterns: DetectedPattern[] = [];
    const values = series.data.map(d => d.value);

    // Trend analysis
    const trendPattern = this.detectTrend(values);
    if (trendPattern) patterns.push(trendPattern);

    // Seasonality analysis
    const seasonalPattern = this.detectSeasonality(values);
    if (seasonalPattern) patterns.push(seasonalPattern);

    // Cyclical analysis
    const cyclicalPattern = this.detectCycles(values);
    if (cyclicalPattern) patterns.push(cyclicalPattern);

    return {
      id: `pattern_${series.id}_${Date.now()}`,
      seriesId: series.id,
      patterns,
      createdAt: new Date().toISOString(),
    };
  }

  // Correlation Analysis
  async analyzeCorrelation(series1: TimeSeries, series2: TimeSeries): Promise<CorrelationAnalysis> {
    const values1 = series1.data.map(d => d.value);
    const values2 = series2.data.map(d => d.value);

    // Ensure both series have the same length
    const minLength = Math.min(values1.length, values2.length);
    const x = values1.slice(0, minLength);
    const y = values2.slice(0, minLength);

    const coefficient = this.calculatePearsonCorrelation(x, y);
    const pValue = this.calculatePValue(coefficient, minLength);
    const significance = this.interpretCorrelation(coefficient);

    return {
      id: `correlation_${series1.id}_${series2.id}_${Date.now()}`,
      series1Id: series1.id,
      series2Id: series2.id,
      coefficient,
      pValue,
      significance,
      createdAt: new Date().toISOString(),
    };
  }

  // Helper methods
  private calculateSeverity(zScore: number, threshold: number): 'low' | 'medium' | 'high' | 'critical' {
    if (zScore > threshold * 2) return 'critical';
    if (zScore > threshold * 1.5) return 'high';
    if (zScore > threshold * 1.2) return 'medium';
    return 'low';
  }

  private classifyAnomalyType(
    value: number,
    expected: number,
    windowValues: number[]
  ): 'spike' | 'dip' | 'trend_change' | 'seasonal_deviation' | 'outlier' {
    const diff = value - expected;
    const avgWindowValue = windowValues.reduce((sum, val) => sum + val, 0) / windowValues.length;

    if (Math.abs(diff) > Math.abs(expected) * 0.5) {
      return diff > 0 ? 'spike' : 'dip';
    }

    if (Math.abs(value - avgWindowValue) > Math.abs(avgWindowValue) * 0.3) {
      return 'trend_change';
    }

    return 'outlier';
  }

  private exponentialSmoothing(values: number[], alpha: number, horizon: number): number[] {
    let smoothed = values[0];
    const forecast: number[] = [];

    // Calculate smoothed values
    for (let i = 1; i < values.length; i++) {
      smoothed = alpha * values[i] + (1 - alpha) * smoothed;
    }

    // Generate forecast
    for (let i = 0; i < horizon; i++) {
      forecast.push(smoothed);
    }

    return forecast;
  }

  private holtsWintersForecasting(
    values: number[],
    alpha: number,
    beta: number,
    gamma: number,
    seasonLength: number,
    horizon: number
  ): number[] {
    const n = values.length;
    const level: number[] = new Array(n);
    const trend: number[] = new Array(n);
    const seasonal: number[] = new Array(n);

    // Initialize
    level[0] = values[0];
    trend[0] = (values[1] - values[0]);

    // Initialize seasonal components
    for (let i = 0; i < seasonLength; i++) {
      seasonal[i] = values[i] / level[0];
    }

    // Update components
    for (let i = 1; i < n; i++) {
      const seasonalIndex = i % seasonLength;
      const prevLevel = level[i - 1];
      const prevTrend = trend[i - 1];
      const prevSeasonal = seasonal[seasonalIndex];

      level[i] = alpha * (values[i] / prevSeasonal) + (1 - alpha) * (prevLevel + prevTrend);
      trend[i] = beta * (level[i] - prevLevel) + (1 - beta) * prevTrend;
      seasonal[i] = gamma * (values[i] / level[i]) + (1 - gamma) * prevSeasonal;
    }

    // Generate forecast
    const forecast: number[] = [];
    const finalLevel = level[n - 1];
    const finalTrend = trend[n - 1];

    for (let h = 1; h <= horizon; h++) {
      const seasonalIndex = (n - 1 + h) % seasonLength;
      const forecastValue = (finalLevel + h * finalTrend) * seasonal[seasonalIndex];
      forecast.push(forecastValue);
    }

    return forecast;
  }

  private detectTrend(values: number[]): DetectedPattern | null {
    if (values.length < 10) return null;

    const n = values.length;
    const x = Array.from({ length: n }, (_, i) => i);
    const y = values;

    // Simple linear regression
    const sumX = x.reduce((sum, val) => sum + val, 0);
    const sumY = y.reduce((sum, val) => sum + val, 0);
    const sumXY = x.reduce((sum, val, i) => sum + val * y[i], 0);
    const sumXX = x.reduce((sum, val) => sum + val * val, 0);

    const slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;

    // Calculate R-squared
    const yMean = sumY / n;
    const ssTotal = y.reduce((sum, val) => sum + Math.pow(val - yMean, 2), 0);
    const ssResidual = y.reduce((sum, val, i) => {
      const predicted = slope * x[i] + intercept;
      return sum + Math.pow(val - predicted, 2);
    }, 0);
    const rSquared = 1 - (ssResidual / ssTotal);

    if (Math.abs(rSquared) < 0.3) return null; // Weak trend

    return {
      type: 'trend',
      description: `${slope > 0 ? 'Increasing' : 'Decreasing'} trend detected`,
      strength: Math.abs(rSquared),
      direction: slope > 0 ? 'increasing' : 'decreasing',
      confidence: Math.abs(rSquared),
    };
  }

  private detectSeasonality(values: number[]): DetectedPattern | null {
    if (values.length < 14) return null;

    // Test for weekly seasonality (7 periods)
    const seasonLength = 7;
    if (values.length < seasonLength * 2) return null;

    const autocorrelation = this.calculateAutocorrelation(values, seasonLength);

    if (Math.abs(autocorrelation) > 0.3) {
      return {
        type: 'seasonal',
        description: `Seasonal pattern detected with period ${seasonLength}`,
        strength: Math.abs(autocorrelation),
        period: seasonLength,
        confidence: Math.abs(autocorrelation),
      };
    }

    return null;
  }

  private detectCycles(values: number[]): DetectedPattern | null {
    // Simplified cycle detection using FFT-like approach
    if (values.length < 20) return null;

    // Look for repeating patterns longer than seasonal
    const maxPeriod = Math.floor(values.length / 3);
    let bestPeriod = 0;
    let bestCorrelation = 0;

    for (let period = 14; period <= maxPeriod; period++) {
      const correlation = this.calculateAutocorrelation(values, period);
      if (Math.abs(correlation) > Math.abs(bestCorrelation)) {
        bestCorrelation = correlation;
        bestPeriod = period;
      }
    }

    if (Math.abs(bestCorrelation) > 0.25) {
      return {
        type: 'cyclical',
        description: `Cyclical pattern detected with period ${bestPeriod}`,
        strength: Math.abs(bestCorrelation),
        period: bestPeriod,
        confidence: Math.abs(bestCorrelation),
      };
    }

    return null;
  }

  private calculateAutocorrelation(values: number[], lag: number): number {
    if (values.length <= lag) return 0;

    const n = values.length - lag;
    const mean = values.reduce((sum, val) => sum + val, 0) / values.length;

    let numerator = 0;
    let denominator = 0;

    for (let i = 0; i < n; i++) {
      numerator += (values[i] - mean) * (values[i + lag] - mean);
    }

    for (let i = 0; i < values.length; i++) {
      denominator += Math.pow(values[i] - mean, 2);
    }

    return denominator === 0 ? 0 : numerator / denominator;
  }

  private calculatePearsonCorrelation(x: number[], y: number[]): number {
    const n = x.length;
    if (n !== y.length || n === 0) return 0;

    const sumX = x.reduce((sum, val) => sum + val, 0);
    const sumY = y.reduce((sum, val) => sum + val, 0);
    const sumXY = x.reduce((sum, val, i) => sum + val * y[i], 0);
    const sumXX = x.reduce((sum, val) => sum + val * val, 0);
    const sumYY = y.reduce((sum, val) => sum + val * val, 0);

    const numerator = n * sumXY - sumX * sumY;
    const denominator = Math.sqrt((n * sumXX - sumX * sumX) * (n * sumYY - sumY * sumY));

    return denominator === 0 ? 0 : numerator / denominator;
  }

  private calculatePValue(correlation: number, n: number): number {
    // Simplified p-value calculation for correlation
    const t = correlation * Math.sqrt((n - 2) / (1 - correlation * correlation));
    // This is a simplified approximation
    return 2 * (1 - this.normalCDF(Math.abs(t)));
  }

  private normalCDF(x: number): number {
    // Simplified normal CDF approximation
    return 0.5 * (1 + this.erf(x / Math.sqrt(2)));
  }

  private erf(x: number): number {
    // Approximation of error function
    const a1 =  0.254829592;
    const a2 = -0.284496736;
    const a3 =  1.421413741;
    const a4 = -1.453152027;
    const a5 =  1.061405429;
    const p  =  0.3275911;

    const sign = x >= 0 ? 1 : -1;
    x = Math.abs(x);

    const t = 1.0 / (1.0 + p * x);
    const y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

    return sign * y;
  }

  private interpretCorrelation(coefficient: number): 'not_significant' | 'weak' | 'moderate' | 'strong' | 'very_strong' {
    const abs = Math.abs(coefficient);
    if (abs < 0.1) return 'not_significant';
    if (abs < 0.3) return 'weak';
    if (abs < 0.5) return 'moderate';
    if (abs < 0.7) return 'strong';
    return 'very_strong';
  }

  private estimateInterval(data: DataPoint[]): number {
    if (data.length < 2) return 60000; // Default to 1 minute

    const intervals: number[] = [];
    for (let i = 1; i < Math.min(data.length, 10); i++) {
      const current = new Date(data[i].timestamp).getTime();
      const previous = new Date(data[i - 1].timestamp).getTime();
      intervals.push(current - previous);
    }

    // Return median interval
    intervals.sort((a, b) => a - b);
    const mid = Math.floor(intervals.length / 2);
    return intervals.length % 2 === 0
      ? (intervals[mid - 1] + intervals[mid]) / 2
      : intervals[mid];
  }
}

export const mlService = MLService.getInstance();