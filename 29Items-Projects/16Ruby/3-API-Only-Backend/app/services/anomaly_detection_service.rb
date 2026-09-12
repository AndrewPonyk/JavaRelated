# frozen_string_literal: true

class AnomalyDetectionService
  MINIMUM_SAMPLE_SIZE = 5
  ERROR_RATE_THRESHOLD = 0.25
  LATENCY_MS_THRESHOLD = 1_000.0

  def initialize(window: 15.minutes)
    @window = window
  end

  def call
    metrics = ApiMetric.where(occurred_at: window.ago..Time.current)
    return [] if metrics.count < MINIMUM_SAMPLE_SIZE

    [detect_error_rate(metrics), detect_latency(metrics)].compact
  end

  private

  attr_reader :window

  def detect_error_rate(metrics)
    total = metrics.count
    error_count = metrics.where(status: 500..).count
    error_rate = error_count.to_f / total
    return if error_rate < ERROR_RATE_THRESHOLD

    create_open_anomaly!(
      metric_name: 'error_rate',
      severity: severity_for_error_rate(error_rate),
      description: "API error rate is #{(error_rate * 100).round(1)}% over the last #{window.inspect}",
      baseline_value: ERROR_RATE_THRESHOLD,
      observed_value: error_rate
    )
  end

  def detect_latency(metrics)
    average_latency = metrics.average(:latency_ms).to_f
    return if average_latency < LATENCY_MS_THRESHOLD

    create_open_anomaly!(
      metric_name: 'latency_ms',
      severity: average_latency >= LATENCY_MS_THRESHOLD * 2 ? 'critical' : 'high',
      description: "Average API latency is #{average_latency.round(2)}ms over the last #{window.inspect}",
      baseline_value: LATENCY_MS_THRESHOLD,
      observed_value: average_latency
    )
  end

  def create_open_anomaly!(attributes)
    existing = Anomaly.open.find_by(metric_name: attributes.fetch(:metric_name))
    return existing if existing

    Anomaly.create!(attributes.merge(detected_at: Time.current))
  end

  def severity_for_error_rate(error_rate)
    return 'critical' if error_rate >= 0.5
    return 'high' if error_rate >= 0.35

    'medium'
  end
end
