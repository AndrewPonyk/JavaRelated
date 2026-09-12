class SprintMetricsJob < ApplicationJob
  queue_as :default

  def perform(sprint_id)
    sprint = Sprint.find(sprint_id)
    cache_key = "sprint/#{sprint.id}/metrics/#{sprint.updated_at.to_i}"

    Rails.cache.write(cache_key, build_metrics(sprint), expires_in: 10.minutes)
  end

  private

  def build_metrics(sprint)
    {
      total_estimate: sprint.total_estimate.to_f,
      logged_hours: sprint.issues.joins(:time_entries).sum("time_entries.hours").to_f,
      completion_ratio: sprint.completion_ratio,
      issues_by_status: sprint.issues.group(:status).count,
      updated_at: Time.current.iso8601
    }
  end
end
