# frozen_string_literal: true

require 'rails_helper'

RSpec.describe 'Metrics and anomalies API', type: :request do
  it 'creates metrics and lists them' do
    user = create(:user)

    post '/api/v1/metrics',
         params: {
           metric: {
             method: 'POST',
             path: '/api/v1/sync',
             status: 200,
             latency_ms: 33.3,
             occurred_at: Time.current.iso8601,
             metadata: { source: 'test' }
           }
         }.to_json,
         headers: auth_headers(user)

    expect(response).to have_http_status(:created)
    expect(json.dig('metric', 'user_id')).to eq(user.id)

    get '/api/v1/metrics', headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.fetch('metrics')).not_to be_empty
    expect(json.dig('meta', 'total_count')).to be >= 1
  end

  it 'rejects invalid metric payloads' do
    user = create(:user)

    post '/api/v1/metrics',
         params: {
           metric: {
             method: 'TRACE',
             path: 'api/v1/sync',
             status: 99,
             latency_ms: -1
           }
         }.to_json,
         headers: auth_headers(user)

    expect(response).to have_http_status(:unprocessable_content)
    expect(json.dig('error', 'code')).to eq('validation_error')
  end

  it 'creates and resolves anomalies' do
    user = create(:user)

    post '/api/v1/anomalies',
         params: {
           anomaly: {
             metric_name: 'latency_ms',
             severity: 'high',
             description: 'High latency',
             baseline_value: 1000,
             observed_value: 1500
           }
         }.to_json,
         headers: auth_headers(user)

    expect(response).to have_http_status(:created)
    anomaly_id = json.dig('anomaly', 'id')

    patch "/api/v1/anomalies/#{anomaly_id}/resolve", headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.dig('anomaly', 'resolved?')).to be(true)
  end

  it 'paginates anomaly lists' do
    user = create(:user)
    create_list(:anomaly, 3)

    get '/api/v1/anomalies?page=1&per_page=2', headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.fetch('anomalies').size).to eq(2)
    expect(json.dig('meta', 'total_count')).to eq(3)
  end
end
