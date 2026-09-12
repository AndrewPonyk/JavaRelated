#!/usr/bin/env ruby
# frozen_string_literal: true

require_relative "../app/services/site_builder"

builder = SiteBuilder.new(
  content_root: ENV.fetch("CONTENT_DIR", File.join("content", "docs")),
  build_root: ENV.fetch("BUILD_DIR", "build")
)

pages = builder.build!
puts "Built #{pages.size} page(s)."
