# frozen_string_literal: true

require "rexml/document"
require_relative "../../app/repositories/page_repository"
require_relative "../../app/services/feed_generator"

RSpec.describe FeedGenerator do
  it "writes a valid RSS feed for generated pages" do
    pages = PageRepository.new.all
    build_dir = ENV.fetch("BUILD_DIR")

    described_class.new(site_name: "Spec Docs", base_url: "https://docs.example").write(build_dir, pages)

    xml = File.read(File.join(build_dir, "feed.xml"))
    document = REXML::Document.new(xml)
    expect(document.elements["rss/channel/title"].text).to eq("Spec Docs")
    expect(xml).to include("https://docs.example/docs/v2/getting-started")
  end
end
