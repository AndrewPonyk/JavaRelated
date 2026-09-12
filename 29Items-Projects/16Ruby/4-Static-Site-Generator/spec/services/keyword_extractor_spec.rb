# frozen_string_literal: true

require_relative "../../app/services/keyword_extractor"

RSpec.describe KeywordExtractor do
  it "extracts deterministic ranked keywords" do
    keywords = described_class.new.extract("Search search documentation generator and docs")

    expect(keywords.first).to eq("search")
    expect(keywords).to include("documentation", "generator")
  end
end
