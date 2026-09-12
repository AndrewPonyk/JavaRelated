# frozen_string_literal: true

require "date"
require "digest"
require "redcarpet"
require "yaml"
require_relative "../errors"
require_relative "../models/page"
require_relative "keyword_extractor"

class MarkdownParser
  FRONT_MATTER_PATTERN = /\A(?:\uFEFF)?[ \t]*---\s*\r?\n(?<yaml>.*?)\r?\n[ \t]*---\s*\r?\n(?<body>.*)\z/m
  SLUG_PATTERN = /\A[a-z0-9]+(?:-[a-z0-9]+)*\z/

  def initialize(keyword_extractor: KeywordExtractor.new)
    @keyword_extractor = keyword_extractor
  end

  def parse_file(path)
    raw = File.read(path)
    front_matter, markdown = split_front_matter(raw, path)
    front_matter = normalize_front_matter(front_matter, path)
    validate_front_matter!(front_matter, path)

    version = infer_version(path)
    slug = front_matter.fetch("slug", File.basename(path, ".md"))
    html = renderer.render(markdown)

    Page.new(
      title: front_matter.fetch("title"),
      slug: slug,
      version: version,
      source_path: path,
      output_path: File.join("docs", version, "#{slug}.html"),
      front_matter: front_matter,
      markdown: markdown,
      html: html,
      keywords: @keyword_extractor.extract("#{front_matter["title"]} #{front_matter["description"]} #{markdown}"),
      checksum: Digest::SHA256.hexdigest(raw)
    )
  end

  private

  def split_front_matter(raw, path)
    match = raw.match(FRONT_MATTER_PATTERN)
    return [{}, raw] unless match

    [YAML.safe_load(match[:yaml], permitted_classes: [Date], aliases: false) || {}, match[:body]]
  rescue Psych::Exception => e
    raise ValidationError.new("#{path}: invalid YAML front matter", details: [e.message])
  end

  def normalize_front_matter(front_matter, path)
    raise ValidationError.new("#{path}: front matter must be a mapping") unless front_matter.is_a?(Hash)

    front_matter = front_matter.transform_keys(&:to_s)
    front_matter["slug"] = File.basename(path, ".md") if front_matter["slug"].to_s.strip.empty?
    front_matter["tags"] = [] if front_matter["tags"].nil?
    front_matter
  end

  def validate_front_matter!(front_matter, path)
    errors = []
    errors << "title is required in front matter" if front_matter["title"].to_s.strip.empty?
    unless front_matter["slug"].match?(SLUG_PATTERN)
      errors << "slug must contain lowercase letters, numbers, and hyphens only"
    end
    errors << "tags must be a YAML sequence" unless front_matter["tags"].is_a?(Array)
    if front_matter["tags"].is_a?(Array) && !front_matter["tags"].all? { |tag| scalar_tag?(tag) }
      errors << "tags must contain only scalar values"
    end

    raise ValidationError.new("#{path}: invalid front matter", details: errors) unless errors.empty?

    front_matter["tags"] = front_matter["tags"].map(&:to_s)
  end

  def scalar_tag?(tag)
    tag.respond_to?(:to_s) && !tag.is_a?(Hash) && !tag.is_a?(Array)
  end

  def infer_version(path)
    parts = path.tr("\\", "/").split("/")
    docs_index = parts.index("docs")
    return parts[docs_index + 1] if docs_index && parts[docs_index + 1]

    "unversioned"
  end

  def renderer
    @renderer ||= Redcarpet::Markdown.new(
      Redcarpet::Render::HTML.new(
        filter_html: true,
        hard_wrap: false,
        safe_links_only: true,
        with_toc_data: true
      ),
      fenced_code_blocks: true,
      tables: true,
      autolink: true
    )
  end
end
