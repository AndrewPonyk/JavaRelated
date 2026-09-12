# frozen_string_literal: true

require_relative "../../app/configuration"

RSpec.describe Configuration do
  it "uses environment variables before YAML defaults" do
    previous = ENV["SITE_NAME"]
    ENV["SITE_NAME"] = "Environment Docs"

    expect(described_class.fetch("SITE_NAME")).to eq("Environment Docs")
  ensure
    ENV["SITE_NAME"] = previous
  end

  it "loads non-secret defaults from settings files" do
    previous = ENV.delete("SITE_NAME")

    expect(described_class.fetch("SITE_NAME")).to eq("Static Site Generator")
  ensure
    ENV["SITE_NAME"] = previous
  end
end
