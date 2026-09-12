require "rails_helper"

RSpec.describe WorkloadPredictionJob, type: :job do
  let(:sprint) { create(:sprint) }

  before do
    ENV["ML_PREDICTOR_URL"] = "http://ml.test"
    ENV["ML_PREDICTOR_API_KEY"] = "test"

    stub_request(:post, "http://ml.test/predict")
      .to_return(status: 200,
                 body: { predicted_hours: 30.0, confidence: 0.9, model_version: "v2" }.to_json,
                 headers: { "Content-Type" => "application/json" })
  end

  it "persists a WorkloadPrediction row" do
    expect { described_class.perform_now(sprint.id) }
      .to change(WorkloadPrediction, :count).by(1)

    prediction = WorkloadPrediction.last
    expect(prediction.predicted_hours).to eq(30.0)
    expect(prediction.model_version).to eq("v2")
  end
end
