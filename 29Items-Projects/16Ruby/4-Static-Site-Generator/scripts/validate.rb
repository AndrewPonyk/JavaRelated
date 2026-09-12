#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require_relative "../app/services/link_validator"

build_dir = ENV.fetch("BUILD_DIR", "build")
required_files = %w[
  index.html
  search/index.json
  search/index.html
  versions.json
  sitemap.xml
  feed.xml
  manifest.json
]

missing = required_files.reject { |path| File.exist?(File.join(build_dir, path)) }
unless missing.empty?
  warn "Missing generated build files: #{missing.join(", ")}"
  exit 1
end

JSON.parse(File.read(File.join(build_dir, "search", "index.json")))
versions = JSON.parse(File.read(File.join(build_dir, "versions.json")))
manifest = JSON.parse(File.read(File.join(build_dir, "manifest.json")))

unless versions["versions"].is_a?(Array) && manifest["pages"].is_a?(Array)
  warn "Generated metadata has an invalid schema"
  exit 1
end

LinkValidator.new.validate!(build_dir)
puts "Build validation passed."
