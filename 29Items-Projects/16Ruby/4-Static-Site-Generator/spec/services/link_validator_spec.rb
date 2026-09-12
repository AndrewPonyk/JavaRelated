# frozen_string_literal: true

require "fileutils"
require_relative "../../app/services/link_validator"

RSpec.describe LinkValidator do
  it "accepts links to generated extensionless pages and assets" do
    build_dir = ENV.fetch("BUILD_DIR")
    FileUtils.mkdir_p(File.join(build_dir, "docs", "v2"))
    FileUtils.mkdir_p(File.join(build_dir, "assets", "css"))
    File.write(File.join(build_dir, "docs", "v2", "intro.html"), '<a href="/docs/v2/install">Install</a>')
    File.write(File.join(build_dir, "docs", "v2", "install.html"), '<a href="/assets/css/site.css">CSS</a>')
    File.write(File.join(build_dir, "assets", "css", "site.css"), "body{}")

    expect(described_class.new.validate!(build_dir)).to be(true)
  end

  it "raises a structured validation error for missing internal links" do
    build_dir = ENV.fetch("BUILD_DIR")
    FileUtils.mkdir_p(build_dir)
    File.write(File.join(build_dir, "index.html"), '<a href="/missing">Missing</a>')

    expect { described_class.new.validate!(build_dir) }
      .to raise_error(ValidationError, /Broken links detected/)
  end

  it "raises a structured validation error for missing asset links" do
    build_dir = ENV.fetch("BUILD_DIR")
    FileUtils.mkdir_p(build_dir)
    File.write(File.join(build_dir, "index.html"), '<link rel="stylesheet" href="/assets/missing.css">')

    expect { described_class.new.validate!(build_dir) }
      .to raise_error(ValidationError, /Broken links detected/)
  end
end
