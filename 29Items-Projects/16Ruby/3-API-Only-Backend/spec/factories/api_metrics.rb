# frozen_string_literal: true

FactoryBot.define do
  factory :api_metric do
    user
    add_attribute(:method) { 'GET' }
    path { '/api/v1/sync/status' }
    status { 200 }
    latency_ms { 25.0 }
    occurred_at { Time.current }
    metadata { { 'request_id' => SecureRandom.uuid } }
  end
end
