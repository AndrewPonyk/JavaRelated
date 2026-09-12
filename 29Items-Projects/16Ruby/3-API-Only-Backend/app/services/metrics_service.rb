# frozen_string_literal: true

class MetricsService
  def self.track_request(request:, response:, user:, started_at:)
    ApiMetric.create!(
      user:,
      method: request.request_method,
      path: request.path,
      status: response.status,
      latency_ms: elapsed_ms(started_at),
      occurred_at: Time.current,
      metadata: {
        request_id: request.request_id,
        format: request.format.symbol.to_s,
        remote_ip: request.remote_ip
      }.compact
    )

    AnomalyDetectionJob.perform_later
  end

  def self.elapsed_ms(started_at)
    return 0.0 unless started_at

    ((Process.clock_gettime(Process::CLOCK_MONOTONIC) - started_at) * 1000).round(2)
  end
end
