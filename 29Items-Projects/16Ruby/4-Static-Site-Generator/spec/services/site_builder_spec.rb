# frozen_string_literal: true

require "json"
require_relative "../../app/services/site_builder"

RSpec.describe SiteBuilder do
  it "builds deterministic static output and metadata files" do
    pages = described_class.new.build!
    build_dir = ENV.fetch("BUILD_DIR")

    expect(pages.size).to eq(3)
    expect(File).to exist(File.join(build_dir, "docs", "v2", "getting-started.html"))
    expect(File).to exist(File.join(build_dir, "search", "index.json"))
    expect(File).to exist(File.join(build_dir, "versions.json"))
    expect(File).to exist(File.join(build_dir, "docs", "index.html"))
    expect(File).to exist(File.join(build_dir, "docs", "v2", "index.html"))
    expect(File).to exist(File.join(build_dir, "sitemap.xml"))
    expect(File).to exist(File.join(build_dir, "feed.xml"))
    expect(File).to exist(File.join(build_dir, "manifest.json"))
    expect(File).to exist(File.join(build_dir, "docs", "latest", "getting-started", "index.html"))

    manifest = JSON.parse(File.read(File.join(build_dir, "manifest.json")))
    expect(manifest["pages"].size).to eq(3)
  end
end
