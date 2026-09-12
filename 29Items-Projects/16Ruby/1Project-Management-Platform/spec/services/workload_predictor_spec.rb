require "rails_helper"

RSpec.describe WorkloadPredictor do
  let(:sprint) { create(:sprint) }

  before do
    ENV["ML_PREDICTOR_URL"] = "http://ml.test"
    ENV["ML_PREDICTOR_API_KEY"] = "test"
    Stoplight::Light.default_data_store.clear_failures("ml-predictor") if defined?(Stoplight)
  end

  it "returns a prediction when the service succeeds" do
    stub_request(:post, "http://ml.test/predict")
      .to_return(status: 200,
                 body: { predicted_hours: 42.5, confidence: 0.8, model_version: "v1" }.to_json,
                 headers: { "Content-Type" => "application/json" })

    result = described_class.new(sprint).call

    expect(result.predicted_hours).to eq(42.5)
    expect(result.confidence).to eq(0.8)
    expect(result.model_version).to eq("v1")
  end

  it "falls back on service failure" do
    stub_request(:post, "http://ml.test/predict").to_return(status: 500)

    result = described_class.new(sprint).call

    expect(result.model_version).to eq("fallback-heuristic")
    expect(result.confidence).to eq(0.4)
  end
end
