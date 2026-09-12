# frozen_string_literal: true

require 'rails_helper'

RSpec.describe AnomalyDetectionService do
  it 'creates an anomaly for elevated error rates' do
    user = create(:user)
    create_list(:api_metric, 3, user:, status: 500)
    create_list(:api_metric, 3, user:, status: 200)

    anomalies = described_class.new.call

    expect(anomalies.map(&:metric_name)).to include('error_rate')
    expect(Anomaly.open.exists?(metric_name: 'error_rate')).to be(true)
  end

  it 'creates an anomaly for high average latency' do
    user = create(:user)
    create_list(:api_metric, 5, user:, latency_ms: 1_500)

    anomalies = described_class.new.call

    expect(anomalies.map(&:metric_name)).to include('latency_ms')
  end
end
