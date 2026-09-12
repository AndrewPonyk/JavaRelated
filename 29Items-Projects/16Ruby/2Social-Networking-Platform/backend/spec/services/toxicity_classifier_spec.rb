require "rails_helper"

RSpec.describe ToxicityClassifier do
  it "classifies local safe and toxic text deterministically" do
    classifier = described_class.new(endpoint: nil)

    expect(classifier.classify(text: "hello").fetch(:label)).to eq("safe")
    expect(classifier.classify(text: "spam abuse threat").fetch(:label)).to eq("toxic")
  end
end
