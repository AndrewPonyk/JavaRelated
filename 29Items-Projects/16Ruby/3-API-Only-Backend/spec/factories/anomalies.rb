# frozen_string_literal: true

FactoryBot.define do
  factory :anomaly do
    metric_name { 'error_rate' }
    severity { 'high' }
    description { 'Elevated API error rate' }
    baseline_value { 0.25 }
    observed_value { 0.5 }
    detected_at { Time.current }
  end
end
