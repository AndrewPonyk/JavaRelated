# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "sqlite3"
require "yaml"
require_relative "../configuration"
require_relative "../errors"
require_relative "../services/markdown_parser"
require_relative "../services/version_index"

class PageRepository
  SLUG_PATTERN = /\A[a-z0-9]+(?:-[a-z0-9]+)*\z/
  VERSION_PATTERN = /\Av[0-9]+(?:\.[0-9]+)*\z/

  def initialize(
    content_root: Configuration.fetch("CONTENT_DIR"),
    database_path: Configuration.fetch("DATABASE_PATH"),
    parser: MarkdownParser.new
  )
    @content_root = content_root
    @database_path = database_path
    @parser = parser
  end

  def migrate!
    FileUtils.mkdir_p(File.dirname(@database_path))
    database.execute("PRAGMA foreign_keys = ON")
    database.execute <<~SQL
      CREATE TABLE IF NOT EXISTS schema_migrations (
        version TEXT PRIMARY KEY,
        applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
      );
    SQL

    Dir.glob(File.join("migrations", "*.sql")).sort.each do |path|
      version = File.basename(path, ".sql")
      next if migration_applied?(version)

      database.execute_batch(File.read(path))
      database.execute("INSERT INTO schema_migrations(version) VALUES (?)", [version])
    end
  end

  def all(sync_metadata: true)
    pages = markdown_paths.map { |path| @parser.parse_file(path) }
    replace_metadata!(pages) if sync_metadata
    pages
  end

  def find(version:, slug:, sync_metadata: true)
    validate_resource_key!(version: version, slug: slug)
    path = source_path_for(version: version, slug: slug)
    raise NotFoundError, "Page #{version}/#{slug} not found" unless File.exist?(path)

    page = @parser.parse_file(path)
    upsert_page!(page) if sync_metadata
    page
  end

  def create(attributes)
    normalized = normalize_attributes(attributes, require_body: true)
    path = path_for(normalized[:version], normalized[:slug])
    raise ConflictError, "Page #{normalized[:version]}/#{normalized[:slug]} already exists" if File.exist?(path)

    write_markdown(path, normalized)
    page = @parser.parse_file(path)
    upsert_page!(page)
    page
  end

  def update(version:, slug:, attributes:)
    validate_resource_key!(version: version, slug: slug)
    current = find(version: version, slug: slug, sync_metadata: true)
    merged = merge_page(current, attributes)
    normalized = normalize_attributes(merged, require_body: true)
    target_path = path_for(normalized[:version], normalized[:slug])

    if target_path != current.source_path && File.exist?(target_path)
      raise ConflictError, "Page #{normalized[:version]}/#{normalized[:slug]} already exists"
    end

    if target_path != current.source_path
      FileUtils.rm_f(current.source_path)
      cleanup_empty_version_directory(current.version)
    end
    write_markdown(target_path, normalized)
    page = @parser.parse_file(target_path)
    all
    page
  end

  def delete(version:, slug:)
    validate_resource_key!(version: version, slug: slug)
    page = find(version: version, slug: slug, sync_metadata: true)
    FileUtils.rm_f(page.source_path)
    delete_metadata!(version: version, slug: slug)
    cleanup_empty_version_directory(version)
    refresh_version_metadata!
    page
  end

  def replace_metadata!(pages)
    migrate!
    database.transaction do
      latest = pages.map(&:version).uniq.sort_by { |version| version_key(version) }.last
      pages.map(&:version).uniq.each { |version| upsert_version!(version, latest: version == latest) }
      existing_keys = pages.map { |page| [page.version, page.slug] }

      page_rows.each do |row|
        key = [row["version"], row["slug"]]
        delete_metadata!(version: row["version"], slug: row["slug"]) unless existing_keys.include?(key)
      end

      pages.each { |page| upsert_page!(page) }
    end
  end

  def page_rows
    migrate!
    database.execute(<<~SQL)
      SELECT pages.*, documentation_versions.version
      FROM pages
      INNER JOIN documentation_versions ON documentation_versions.id = pages.version_id
      ORDER BY documentation_versions.version DESC, pages.slug ASC
    SQL
  end

  def close
    @database&.close
  end

  private

  def database
    @database ||= SQLite3::Database.new(@database_path).tap do |db|
      db.results_as_hash = true
      db.execute("PRAGMA foreign_keys = ON")
      db.busy_timeout = 5000
    end
  end

  def migration_applied?(version)
    row = database.get_first_row("SELECT version FROM schema_migrations WHERE version = ?", [version])
    !row.nil?
  end

  def markdown_paths
    Dir.glob(File.join(@content_root, "**", "*.md")).sort
  end

  def path_for(version, slug)
    File.join(@content_root, version.to_s, "#{slug}.md")
  end

  def source_path_for(version:, slug:)
    direct_path = path_for(version, slug)
    return direct_path if File.exist?(direct_path)

    markdown_paths
      .select { |path| File.dirname(path).tr("\\", "/").end_with?("/#{version}") }
      .find do |path|
        page = @parser.parse_file(path)
        page.version == version && page.slug == slug
      rescue ValidationError
        false
      end || direct_path
  end

  def validate_resource_key!(version:, slug:)
    errors = []
    version = version.to_s
    slug = slug.to_s

    errors << "version must use v-prefixed numeric format such as v1 or v2.1" unless version.match?(VERSION_PATTERN)
    errors << "slug must contain lowercase letters, numbers, and hyphens only" unless slug.match?(SLUG_PATTERN)

    raise ValidationError.new("Invalid page identifier", details: errors) unless errors.empty?
  end

  def normalize_attributes(attributes, require_body:)
    attrs = stringify_keys(attributes)
    errors = []
    title = attrs["title"].to_s.strip
    slug = attrs["slug"].to_s.strip
    version = attrs["version"].to_s.strip
    body = attrs["body"].to_s
    description = attrs["description"].to_s.strip
    tags = normalize_tags(attrs["tags"])
    canonical_url = attrs["canonical_url"].to_s.strip

    errors << "title is required" if title.empty?
    errors << "slug is required" if slug.empty?
    errors << "version is required" if version.empty?
    errors << "body is required" if require_body && body.strip.empty?
    if !slug.empty? && !slug.match?(SLUG_PATTERN)
      errors << "slug must contain lowercase letters, numbers, and hyphens only"
    end
    if !version.empty? && !version.match?(VERSION_PATTERN)
      errors << "version must use v-prefixed numeric format such as v1 or v2.1"
    end
    errors << "tags must be an array of strings" unless tags

    raise ValidationError.new(details: errors) unless errors.empty?

    {
      title: title,
      slug: slug,
      version: version,
      body: body,
      description: description,
      tags: tags,
      canonical_url: canonical_url
    }
  end

  def stringify_keys(attributes)
    attributes.each_with_object({}) { |(key, value), memo| memo[key.to_s] = value }
  end

  def normalize_tags(value)
    return [] if value.nil? || value == ""
    return nil unless value.is_a?(Array)
    return nil unless value.all? { |tag| scalar_tag?(tag) }

    value.map { |tag| tag.to_s.strip }.reject(&:empty?)
  end

  def scalar_tag?(tag)
    tag.respond_to?(:to_s) && !tag.is_a?(Hash) && !tag.is_a?(Array)
  end

  def merge_page(page, attributes)
    attrs = stringify_keys(attributes)
    {
      title: attrs.fetch("title", page.title),
      slug: attrs.fetch("slug", page.slug),
      version: attrs.fetch("version", page.version),
      body: attrs.fetch("body", page.markdown),
      description: attrs.fetch("description", page.description),
      tags: attrs.fetch("tags", page.tags),
      canonical_url: attrs.fetch("canonical_url", page.canonical_url)
    }
  end

  def write_markdown(path, attributes)
    FileUtils.mkdir_p(File.dirname(path))
    front_matter = {
      "title" => attributes[:title],
      "slug" => attributes[:slug],
      "description" => attributes[:description],
      "tags" => attributes[:tags],
      "canonical_url" => attributes[:canonical_url]
    }.reject { |_key, value| value.respond_to?(:empty?) ? value.empty? : value.nil? }

    File.write(path, "#{front_matter.to_yaml}---\n\n#{attributes[:body].rstrip}\n")
  end

  def upsert_version!(version, latest: false)
    database.execute(
      "INSERT INTO documentation_versions(version, is_latest) VALUES (?, ?) " \
      "ON CONFLICT(version) DO UPDATE SET is_latest = excluded.is_latest",
      [version, latest ? 1 : 0]
    )
  end

  def upsert_page!(page)
    migrate!
    latest = VersionIndex.new(content_root: @content_root).latest
    upsert_version!(page.version, latest: page.version == latest)
    version_id = database.get_first_value("SELECT id FROM documentation_versions WHERE version = ?", [page.version])

    database.execute(
      <<~SQL,
        INSERT INTO pages(
          version_id, title, slug, source_path, output_path, description, canonical_url, tags, checksum, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(version_id, slug) DO UPDATE SET
          title = excluded.title,
          source_path = excluded.source_path,
          output_path = excluded.output_path,
          description = excluded.description,
          canonical_url = excluded.canonical_url,
          tags = excluded.tags,
          checksum = excluded.checksum,
          updated_at = CURRENT_TIMESTAMP
      SQL
      [
        version_id,
        page.title,
        page.slug,
        page.source_path,
        page.output_path,
        page.description,
        page.canonical_url,
        JSON.generate(page.tags),
        page.checksum || Digest::SHA256.file(page.source_path).hexdigest
      ]
    )

    page_id = database.get_first_value(
      "SELECT id FROM pages WHERE version_id = ? AND slug = ?",
      [version_id, page.slug]
    )
    database.execute("DELETE FROM page_keywords WHERE page_id = ?", [page_id])
    page.keywords.uniq.each_with_index do |keyword, index|
      database.execute(
        "INSERT INTO page_keywords(page_id, keyword, weight) VALUES (?, ?, ?)",
        [page_id, keyword, 1.0 / (index + 1)]
      )
    end
  end

  def delete_metadata!(version:, slug:)
    version_id = database.get_first_value("SELECT id FROM documentation_versions WHERE version = ?", [version])
    return unless version_id

    page_id = database.get_first_value("SELECT id FROM pages WHERE version_id = ? AND slug = ?", [version_id, slug])
    return unless page_id

    database.execute("DELETE FROM page_keywords WHERE page_id = ?", [page_id])
    database.execute("DELETE FROM pages WHERE id = ?", [page_id])
  end

  def cleanup_empty_version_directory(version)
    path = File.join(@content_root, version)
    Dir.rmdir(path) if Dir.exist?(path) && Dir.empty?(path)
  end

  def refresh_version_metadata!
    database.execute("DELETE FROM documentation_versions WHERE id NOT IN (SELECT DISTINCT version_id FROM pages)")
    latest = VersionIndex.new(content_root: @content_root).latest
    database.execute("UPDATE documentation_versions SET is_latest = 0")
    database.execute("UPDATE documentation_versions SET is_latest = 1 WHERE version = ?", [latest]) if latest
  end

  def version_key(version)
    version.to_s.sub(/\Av/, "").split(".").map { |part| part.to_i }
  end
end
