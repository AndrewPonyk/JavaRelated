# frozen_string_literal: true

require "rexml/document"
require_relative "../../app/repositories/page_repository"
require_relative "../../app/services/sitemap_generator"

RSpec.describe SitemapGenerator do
  it "writes a valid sitemap with canonical page locations" do
    pages = PageRepository.new.all
    build_dir = ENV.fetch("BUILD_DIR")

    described_class.new(base_url: "https://docs.example/").write(build_dir, pages)

    xml = File.read(File.join(build_dir, "sitemap.xml"))
    document = REXML::Document.new(xml)
    expect(document.root.name).to eq("urlset")
    expect(xml).to include("https://docs.example/docs/v2/install")
  end
end
