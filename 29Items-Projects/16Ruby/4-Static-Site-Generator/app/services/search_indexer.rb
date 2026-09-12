# frozen_string_literal: true

require "fileutils"
require "json"

class SearchIndexer
  DEFAULT_INDEX_PATH = File.join("public", "search", "index.json")

  def initialize(index_path: DEFAULT_INDEX_PATH)
    @index_path = index_path
  end

  def build(pages, include_body: false)
    pages.map do |page|
      record = {
        title: page.title,
        slug: page.slug,
        version: page.version,
        url: "/docs/#{page.version}/#{page.slug}",
        keywords: page.keywords,
        summary: page.description,
        tags: page.tags
      }
      record[:body] = strip_html(page.html) if include_body
      record
    end
  end

  def write(pages)
    FileUtils.mkdir_p(File.dirname(@index_path))
    File.write(@index_path, JSON.pretty_generate(build(pages)))
  end

  def search(query, records: nil, limit: 10)
    limit = [[limit.to_i, 1].max, 50].min
    query = query.to_s.strip
    records ||= load_index
    records = records.map { |record| stringify_record(record) }
    return records.first(limit).map { |record| public_record(record) } if query.empty?

    normalized = query.downcase
    records
      .map { |record| [score(record, normalized), record] }
      .select { |score, _record| score.positive? }
      .sort_by { |score, record| [-score, record["title"]] }
      .first(limit)
      .map { |_score, record| public_record(record) }
  end

  private

  def load_index
    return [] unless File.exist?(@index_path)

    JSON.parse(File.read(@index_path))
  rescue JSON::ParserError
    []
  end

  def score(record, query)
    score = 0
    score += 10 if record["title"].downcase.include?(query)
    score += 6 if Array(record["keywords"]).join(" ").downcase.include?(query)
    score += 4 if Array(record["tags"]).join(" ").downcase.include?(query)
    score += 3 if record["summary"].downcase.include?(query)
    score += 1 if record["body"].to_s.downcase.include?(query)
    score
  end

  def public_record(record)
    record.reject { |key, _value| key == "body" }
  end

  def stringify_record(record)
    record.transform_keys(&:to_s)
  end

  def strip_html(html)
    html.to_s.gsub(/<[^>]+>/, " ").gsub(/\s+/, " ").strip
  end
end
