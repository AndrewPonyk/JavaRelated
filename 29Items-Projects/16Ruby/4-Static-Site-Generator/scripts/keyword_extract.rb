#!/usr/bin/env ruby
# frozen_string_literal: true

require_relative "../app/services/keyword_extractor"

path = ARGV.fetch(0) do
  warn "Usage: ruby scripts/keyword_extract.rb path/to/file.md"
  exit 1
end

keywords = KeywordExtractor.new.extract(File.read(path))
puts keywords.join(", ")
