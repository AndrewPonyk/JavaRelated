import React, { useState, useEffect } from 'react';
import { Brain, TrendingUp, AlertTriangle, Target, Activity, Zap, BarChart3, Users } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { analyticsService } from '@/services/ml/AnalyticsService';
import { TimeSeries } from '@/services/ml/types';
import { useToastHelpers } from '@/components/ui/Toaster';

// Mock data for demonstration
const generateMockTimeSeries = (id: string, name: string, baseValue: number, trend: number = 0, noise: number = 10): TimeSeries => {
  const data = Array.from({ length: 60 }, (_, i) => {
    const timestamp = new Date(Date.now() - (59 - i) * 60 * 60 * 1000).toISOString();
    const trendValue = baseValue + (trend * i);
    const seasonalValue = Math.sin(i * 0.1) * 20;
    const randomNoise = (Math.random() - 0.5) * noise;
    const value = Math.max(0, trendValue + seasonalValue + randomNoise);

    return { timestamp, value };
  });

  return { id, name, data };
};

export const MLAnalytics: React.FC = () => {
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [analysisResults, setAnalysisResults] = useState<any>(null);
  const [selectedDemo, setSelectedDemo] = useState<'anomaly' | 'forecast' | 'insights' | 'patterns'>('anomaly');
  const { success: showSuccess, error: showError } = useToastHelpers();

  // Mock data
  const mockSeries = [
    generateMockTimeSeries('revenue', 'Monthly Revenue', 100000, 500, 5000),
    generateMockTimeSeries('users', 'Active Users', 50000, 200, 2000),
    generateMockTimeSeries('conversion', 'Conversion Rate', 3.2, 0.01, 0.5),
    generateMockTimeSeries('performance', 'System Performance', 95, -0.1, 3),
  ];

  useEffect(() => {
    // Initialize analytics service on component mount
    analyticsService.initialize().catch(console.error);
  }, []);

  const handleRunAnalysis = async () => {
    setIsAnalyzing(true);
    try {
      const results = await analyticsService.analyzeDashboardData(mockSeries, {
        includeAnomalies: true,
        includeForecasts: true,
        includeInsights: true,
        includePatterns: true,
        includeCorrelations: true,
      });

      setAnalysisResults(results);
      showSuccess('Analysis Complete', 'ML analysis has been completed successfully');
    } catch (error) {
      console.error('Analysis failed:', error);
      showError('Analysis Failed', 'Unable to complete ML analysis. Please try again.');
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleGenerateReport = async () => {
    try {
      const report = await analyticsService.generateAnalyticsReport(mockSeries, {
        type: 'summary',
        includeCharts: true,
        includeTables: true,
      });

      showSuccess('Report Generated', `Analytics report "${report.title}" has been created`);
    } catch (error) {
      console.error('Report generation failed:', error);
      showError('Report Failed', 'Unable to generate analytics report. Please try again.');
    }
  };

  const renderAnalysisResults = () => {
    if (!analysisResults) return null;

    const { anomalies, forecasts, insights, patterns, correlations, summary } = analysisResults;

    return (
      <div className="space-y-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card className="p-4">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-red-100 rounded-lg">
                <AlertTriangle className="h-5 w-5 text-red-600" />
              </div>
              <div>
                <div className="text-2xl font-bold text-gray-900">{summary.totalAnomalies}</div>
                <div className="text-sm text-gray-600">Anomalies Detected</div>
              </div>
            </div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-blue-100 rounded-lg">
                <Brain className="h-5 w-5 text-blue-600" />
              </div>
              <div>
                <div className="text-2xl font-bold text-gray-900">{summary.totalInsights}</div>
                <div className="text-sm text-gray-600">AI Insights</div>
              </div>
            </div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-green-100 rounded-lg">
                <Target className="h-5 w-5 text-green-600" />
              </div>
              <div>
                <div className="text-2xl font-bold text-gray-900">
                  {(summary.avgForecastConfidence * 100).toFixed(0)}%
                </div>
                <div className="text-sm text-gray-600">Forecast Confidence</div>
              </div>
            </div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-purple-100 rounded-lg">
                <Activity className="h-5 w-5 text-purple-600" />
              </div>
              <div>
                <div className="text-2xl font-bold text-gray-900">
                  {(summary.dataQualityScore * 100).toFixed(0)}%
                </div>
                <div className="text-sm text-gray-600">Data Quality</div>
              </div>
            </div>
          </Card>
        </div>

        {/* Detailed Results Tabs */}
        <div className="space-y-4">
          <div className="flex space-x-2">
            {[
              { id: 'anomaly', label: 'Anomalies', icon: AlertTriangle },
              { id: 'forecast', label: 'Forecasts', icon: Target },
              { id: 'insights', label: 'Insights', icon: Brain },
              { id: 'patterns', label: 'Patterns', icon: Activity },
            ].map(tab => (
              <Button
                key={tab.id}
                variant={selectedDemo === tab.id ? 'default' : 'outline'}
                size="sm"
                onClick={() => setSelectedDemo(tab.id as any)}
              >
                <tab.icon className="mr-2 h-4 w-4" />
                {tab.label}
              </Button>
            ))}
          </div>

          <Card className="p-6">
            {selectedDemo === 'anomaly' && (
              <div className="space-y-4">
                <h3 className="text-lg font-semibold">Anomaly Detection Results</h3>
                {Object.entries(anomalies).map(([seriesId, seriesAnomalies]: [string, any[]]) => {
                  const series = mockSeries.find(s => s.id === seriesId);
                  return (
                    <div key={seriesId} className="border rounded-lg p-4">
                      <div className="flex items-center justify-between mb-3">
                        <h4 className="font-medium">{series?.name}</h4>
                        <Badge variant={seriesAnomalies.length > 0 ? 'destructive' : 'success'}>
                          {seriesAnomalies.length} anomalies
                        </Badge>
                      </div>
                      {seriesAnomalies.length > 0 ? (
                        <div className="space-y-2">
                          {seriesAnomalies.slice(0, 3).map((anomaly: any, idx: number) => (
                            <div key={idx} className="text-sm bg-red-50 p-2 rounded">
                              <div className="font-medium text-red-800">
                                {anomaly.type.replace('_', ' ').toUpperCase()}
                              </div>
                              <div className="text-red-600">{anomaly.description}</div>
                              <div className="text-xs text-red-500 mt-1">
                                Severity: {anomaly.severity} | Score: {(anomaly.score * 100).toFixed(1)}%
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="text-sm text-green-600 bg-green-50 p-2 rounded">
                          No anomalies detected. Data appears normal.
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}

            {selectedDemo === 'forecast' && (
              <div className="space-y-4">
                <h3 className="text-lg font-semibold">Forecast Results</h3>
                {Object.entries(forecasts).map(([seriesId, forecast]: [string, any]) => {
                  const series = mockSeries.find(s => s.id === seriesId);
                  return (
                    <div key={seriesId} className="border rounded-lg p-4">
                      <div className="flex items-center justify-between mb-3">
                        <h4 className="font-medium">{series?.name}</h4>
                        <div className="flex space-x-2">
                          <Badge variant="outline">
                            {forecast.horizon} periods
                          </Badge>
                          <Badge variant={forecast.confidence > 0.7 ? 'success' : 'warning'}>
                            {(forecast.confidence * 100).toFixed(0)}% confidence
                          </Badge>
                        </div>
                      </div>
                      <div className="space-y-2">
                        <div className="text-sm bg-blue-50 p-2 rounded">
                          <div className="font-medium text-blue-800">Model: {forecast.model}</div>
                          <div className="text-blue-600">
                            Predictions: {forecast.predictions.length} future data points
                          </div>
                        </div>
                        {forecast.predictions.slice(0, 3).map((pred: any, idx: number) => (
                          <div key={idx} className="text-xs text-gray-600 flex justify-between">
                            <span>{new Date(pred.timestamp).toLocaleDateString()}</span>
                            <span className="font-medium">{pred.value.toFixed(2)}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {selectedDemo === 'insights' && (
              <div className="space-y-4">
                <h3 className="text-lg font-semibold">AI-Generated Insights</h3>
                <div className="space-y-3">
                  {insights.slice(0, 5).map((insight: any, idx: number) => (
                    <div key={idx} className="border rounded-lg p-4">
                      <div className="flex items-start justify-between mb-2">
                        <h4 className="font-medium text-gray-900">{insight.title}</h4>
                        <div className="flex space-x-2">
                          <Badge
                            variant={
                              insight.severity === 'critical'
                                ? 'destructive'
                                : insight.severity === 'warning'
                                ? 'warning'
                                : 'secondary'
                            }
                          >
                            {insight.severity}
                          </Badge>
                          <Badge variant="outline">
                            {(insight.confidence * 100).toFixed(0)}% confidence
                          </Badge>
                        </div>
                      </div>
                      <p className="text-sm text-gray-600 mb-2">{insight.description}</p>
                      <div className="flex items-center justify-between text-xs text-gray-400">
                        <span className="capitalize">Type: {insight.type}</span>
                        <span>{new Date(insight.timestamp).toLocaleString()}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {selectedDemo === 'patterns' && (
              <div className="space-y-4">
                <h3 className="text-lg font-semibold">Pattern Analysis</h3>
                {Object.entries(patterns).map(([seriesId, pattern]: [string, any]) => {
                  const series = mockSeries.find(s => s.id === seriesId);
                  return (
                    <div key={seriesId} className="border rounded-lg p-4">
                      <div className="flex items-center justify-between mb-3">
                        <h4 className="font-medium">{series?.name}</h4>
                        <Badge variant="outline">
                          {pattern.patterns.length} patterns
                        </Badge>
                      </div>
                      {pattern.patterns.length > 0 ? (
                        <div className="space-y-2">
                          {pattern.patterns.map((p: any, idx: number) => (
                            <div key={idx} className="text-sm bg-gray-50 p-2 rounded">
                              <div className="font-medium text-gray-800 capitalize">
                                {p.type} Pattern
                              </div>
                              <div className="text-gray-600">{p.description}</div>
                              <div className="text-xs text-gray-500 mt-1">
                                Strength: {(p.strength * 100).toFixed(1)}% | Confidence: {(p.confidence * 100).toFixed(1)}%
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="text-sm text-gray-500">No significant patterns detected</div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </div>

        {/* Correlations */}
        {correlations.length > 0 && (
          <Card className="p-6">
            <h3 className="text-lg font-semibold mb-4">Data Correlations</h3>
            <div className="space-y-3">
              {correlations.map((corr: any, idx: number) => {
                const series1 = mockSeries.find(s => s.id === corr.series1Id);
                const series2 = mockSeries.find(s => s.id === corr.series2Id);
                return (
                  <div key={idx} className="flex items-center justify-between p-3 bg-gray-50 rounded">
                    <div>
                      <div className="font-medium">
                        {series1?.name} ↔ {series2?.name}
                      </div>
                      <div className="text-sm text-gray-600">
                        Correlation: {(corr.coefficient * 100).toFixed(1)}%
                      </div>
                    </div>
                    <Badge
                      variant={
                        corr.significance === 'very_strong' || corr.significance === 'strong'
                          ? 'default'
                          : 'secondary'
                      }
                    >
                      {corr.significance.replace('_', ' ')}
                    </Badge>
                  </div>
                );
              })}
            </div>
          </Card>
        )}
      </div>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center">
            <Brain className="mr-3 h-6 w-6 text-blue-600" />
            ML & Analytics Platform
          </h1>
          <p className="text-gray-600 mt-1">
            AI-powered analytics, anomaly detection, and predictive insights
          </p>
        </div>
        <div className="flex space-x-3">
          <Button variant="outline" onClick={handleGenerateReport}>
            <BarChart3 className="mr-2 h-4 w-4" />
            Generate Report
          </Button>
          <Button onClick={handleRunAnalysis} disabled={isAnalyzing}>
            {isAnalyzing ? (
              <>
                <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent mr-2" />
                Analyzing...
              </>
            ) : (
              <>
                <Zap className="mr-2 h-4 w-4" />
                Run Analysis
              </>
            )}
          </Button>
        </div>
      </div>

      {/* Features Overview */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <Card className="p-6 text-center">
          <div className="w-12 h-12 bg-red-100 rounded-lg mx-auto mb-4 flex items-center justify-center">
            <AlertTriangle className="h-6 w-6 text-red-600" />
          </div>
          <h3 className="font-semibold mb-2">Anomaly Detection</h3>
          <p className="text-sm text-gray-600">
            Real-time detection of unusual patterns and outliers in your data
          </p>
        </Card>

        <Card className="p-6 text-center">
          <div className="w-12 h-12 bg-orange-100 rounded-lg mx-auto mb-4 flex items-center justify-center">
            <Target className="h-6 w-6 text-orange-600" />
          </div>
          <h3 className="font-semibold mb-2">Predictive Forecasting</h3>
          <p className="text-sm text-gray-600">
            Machine learning-powered forecasts with confidence intervals
          </p>
        </Card>

        <Card className="p-6 text-center">
          <div className="w-12 h-12 bg-blue-100 rounded-lg mx-auto mb-4 flex items-center justify-center">
            <Brain className="h-6 w-6 text-blue-600" />
          </div>
          <h3 className="font-semibold mb-2">AI Insights</h3>
          <p className="text-sm text-gray-600">
            Intelligent insights and recommendations from your data
          </p>
        </Card>

        <Card className="p-6 text-center">
          <div className="w-12 h-12 bg-green-100 rounded-lg mx-auto mb-4 flex items-center justify-center">
            <TrendingUp className="h-6 w-6 text-green-600" />
          </div>
          <h3 className="font-semibold mb-2">Pattern Recognition</h3>
          <p className="text-sm text-gray-600">
            Identify trends, seasonality, and cyclical patterns automatically
          </p>
        </Card>
      </div>

      {/* Data Sources */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold mb-4">Sample Data Sources</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {mockSeries.map(series => (
            <div key={series.id} className="bg-gray-50 p-4 rounded-lg">
              <div className="flex items-center space-x-2 mb-2">
                <Users className="h-4 w-4 text-gray-600" />
                <span className="font-medium">{series.name}</span>
              </div>
              <div className="text-sm text-gray-600">
                {series.data.length} data points
              </div>
              <div className="text-xs text-gray-500 mt-1">
                Latest: {series.data[series.data.length - 1]?.value.toFixed(2)}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Analysis Results */}
      {renderAnalysisResults()}

      {/* Call to Action */}
      {!analysisResults && (
        <Card className="p-8 text-center">
          <Brain className="h-12 w-12 text-blue-500 mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">Ready to Analyze Your Data?</h3>
          <p className="text-gray-600 mb-6">
            Click "Run Analysis" to see AI-powered insights, anomaly detection, and forecasting in action.
          </p>
          <Button size="lg" onClick={handleRunAnalysis} disabled={isAnalyzing}>
            <Zap className="mr-2 h-5 w-5" />
            Start ML Analysis
          </Button>
        </Card>
      )}
    </div>
  );
};

export default MLAnalytics;