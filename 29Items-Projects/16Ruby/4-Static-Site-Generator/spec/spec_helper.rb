# frozen_string_literal: true

require "simplecov"
SimpleCov.start do
  enable_coverage :branch
  minimum_coverage 80
  add_filter "/spec/"
end

ENV["APP_ENV"] = "test"
ENV["CONTENT_DIR"] = File.join("tmp", "spec_content", "docs")
ENV["BUILD_DIR"] = File.join("tmp", "spec_build")
ENV["DATABASE_PATH"] = File.join("tmp", "spec_site.db")
ENV["PUBLIC_DIR"] = "public"
ENV["SITE_BASE_URL"] = "http://example.test"
ENV["SITE_NAME"] = "Spec Docs"

require "fileutils"
require "rack/test"
require "rspec"

require_relative "../app"

RSpec.configure do |config|
  config.include Rack::Test::Methods
  config.order = :random

  config.before do
    FileUtils.rm_rf(File.join("tmp", "spec_content"))
    FileUtils.rm_rf(ENV.fetch("BUILD_DIR"))
    FileUtils.rm_f(ENV.fetch("DATABASE_PATH"))
    FileUtils.mkdir_p(File.dirname(ENV.fetch("CONTENT_DIR")))
    FileUtils.cp_r(File.join("spec", "fixtures", "content", "docs"), ENV.fetch("CONTENT_DIR"))
  end

  def app
    StaticSiteGeneratorApp
  end
end
