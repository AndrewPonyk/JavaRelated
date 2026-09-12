# frozen_string_literal: true

require_relative "../../app/repositories/page_repository"
require_relative "../../app/services/search_indexer"

RSpec.describe SearchIndexer do
  it "builds and searches ranked records" do
    pages = PageRepository.new.all
    records = described_class.new.build(pages)
    results = described_class.new.search("install", records: records)

    expect(results.first["slug"]).to eq("install")
    expect(results.first).not_to have_key("body")
  end

  it "clamps search limits to a bounded range" do
    pages = PageRepository.new.all
    results = described_class.new.search("", records: described_class.new.build(pages), limit: 500)

    expect(results.size).to eq(3)
  end
end
