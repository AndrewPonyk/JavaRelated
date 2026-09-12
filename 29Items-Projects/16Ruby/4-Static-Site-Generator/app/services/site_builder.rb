# frozen_string_literal: true

require "fileutils"
require "erb"
require "json"
require "logger"
require "time"
require_relative "../configuration"
require_relative "../repositories/page_repository"
require_relative "feed_generator"
require_relative "link_validator"
require_relative "markdown_parser"
require_relative "redirect_manager"
require_relative "search_indexer"
require_relative "sitemap_generator"
require_relative "template_renderer"
require_relative "version_index"

class SiteBuilder
  def initialize(
    content_root: Configuration.fetch("CONTENT_DIR"),
    build_root: Configuration.fetch("BUILD_DIR"),
    public_root: Configuration.fetch("PUBLIC_DIR"),
    site_name: Configuration.fetch("SITE_NAME"),
    base_url: Configuration.fetch("SITE_BASE_URL"),
    parser: MarkdownParser.new,
    renderer: TemplateRenderer.new,
    search_indexer: nil,
    version_index: VersionIndex.new(content_root: content_root),
    repository: nil,
    logger: Logger.new($stdout)
  )
    @content_root = content_root
    @build_root = build_root
    @public_root = public_root
    @site_name = site_name
    @base_url = base_url
    @parser = parser
    @renderer = renderer
    @search_indexer = search_indexer || SearchIndexer.new(index_path: File.join(build_root, "search", "index.json"))
    @version_index = version_index
    @repository = repository || PageRepository.new(content_root: content_root)
    @logger = logger
  end

  def build!
    started_at = Time.now
    @logger.info({ event: "build_started", content_root: @content_root, build_root: @build_root }.to_json)
    @repository.migrate!
    pages = discover_pages
    validate_unique_pages!(pages)
    versions = @version_index.discover
    version_links = version_links_for(pages)
    existing_checksums = previous_checksums

    prepare_build_directory
    copy_public_assets

    pages.each do |page|
      output_path = File.join(@build_root, page.output_path)
      FileUtils.mkdir_p(File.dirname(output_path))
      File.write(output_path, @renderer.render_page(page, versions: versions, version_links: version_links))
    end

    @search_indexer.write(pages)
    write_search_page(versions, version_links)
    write_docs_index(pages, versions, version_links)
    write_index(versions, version_links)
    write_versions(versions)
    RedirectManager.new.write(@build_root)
    SitemapGenerator.new(base_url: @base_url).write(@build_root, pages)
    FeedGenerator.new(site_name: @site_name, base_url: @base_url).write(@build_root, pages)
    LinkValidator.new.validate!(@build_root)
    @repository.replace_metadata!(pages)
    write_manifest(pages, existing_checksums, started_at)
    @logger.info({ event: "build_completed", pages: pages.size, seconds: elapsed(started_at) }.to_json)
    pages
  end

  def discover_pages(sync_metadata: true)
    @repository.all(sync_metadata: sync_metadata)
  end

  def find_page(version:, slug:, sync_metadata: false)
    @repository.find(version: version, slug: slug, sync_metadata: sync_metadata)
  end

  def search(query, limit: 10)
    records = @search_indexer.build(discover_pages(sync_metadata: false), include_body: true)
    @search_indexer.search(query, records: records, limit: limit)
  end

  private

  def previous_checksums
    @repository.page_rows.each_with_object({}) do |row, memo|
      memo["#{row["version"]}/#{row["slug"]}"] = row["checksum"]
    end
  end

  def prepare_build_directory
    FileUtils.mkdir_p(@build_root)
    Dir.glob(File.join(@build_root, "*")).each { |path| FileUtils.rm_rf(path) }
  end

  def copy_public_assets
    return unless Dir.exist?(@public_root)

    FileUtils.mkdir_p(@build_root)
    Dir.glob(File.join(@public_root, "*")).reject { |path| File.basename(path) == "search" }.each do |path|
      FileUtils.cp_r(path, @build_root)
    end
  end

  def write_index(versions, version_links)
    latest = versions.first
    landing_slug = version_links[latest]
    body = if latest && landing_slug
             redirect_html("/docs/#{latest}/#{landing_slug}")
           else
             "<!doctype html><html><body><h1>No documentation pages found</h1></body></html>"
           end
    File.write(File.join(@build_root, "index.html"), body)
  end

  def write_search_page(versions, version_links)
    html = @renderer.render_search(versions: versions, version_links: version_links)
    File.write(File.join(@build_root, "search.html"), html)
    FileUtils.mkdir_p(File.join(@build_root, "search"))
    File.write(File.join(@build_root, "search", "index.html"), html)
  end

  def write_docs_index(pages, versions, version_links)
    FileUtils.mkdir_p(File.join(@build_root, "docs"))
    File.write(
      File.join(@build_root, "docs", "index.html"),
      @renderer.render_docs_index(pages: pages, versions: versions, version_links: version_links)
    )

    versions.each do |version|
      version_pages = pages.select { |page| page.version == version }
      FileUtils.mkdir_p(File.join(@build_root, "docs", version))
      File.write(
        File.join(@build_root, "docs", version, "index.html"),
        @renderer.render_docs_index(
          pages: version_pages,
          versions: versions,
          version_links: version_links,
          selected_version: version
        )
      )
    end
  end

  def write_versions(versions)
    File.write(
      File.join(@build_root, "versions.json"),
      JSON.pretty_generate({ latest: versions.first, versions: versions })
    )
  end

  def redirect_html(destination)
    escaped_destination = ERB::Util.html_escape(destination)
    <<~HTML
      <!doctype html>
      <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta http-equiv="refresh" content="0; url=#{escaped_destination}">
          <link rel="canonical" href="#{escaped_destination}">
          <title>Redirecting</title>
        </head>
        <body>
          <p>Redirecting to <a href="#{escaped_destination}">#{escaped_destination}</a>.</p>
        </body>
      </html>
    HTML
  end

  def validate_unique_pages!(pages)
    duplicates = pages.group_by { |page| [page.version, page.slug] }.select { |_key, group| group.size > 1 }
    return if duplicates.empty?

    details = duplicates.map { |(version, slug), group| "#{version}/#{slug}: #{group.map(&:source_path).join(", ")}" }
    raise ValidationError.new("Duplicate documentation pages detected", details: details)
  end

  def version_links_for(pages)
    pages.group_by(&:version).transform_values do |version_pages|
      preferred = version_pages.find { |page| page.slug == "getting-started" }
      (preferred || version_pages.min_by(&:slug)).slug
    end
  end

  def write_manifest(pages, previous_checksums, started_at)
    records = pages.map do |page|
      key = "#{page.version}/#{page.slug}"
      {
        title: page.title,
        version: page.version,
        slug: page.slug,
        output_path: page.output_path,
        checksum: page.checksum,
        changed: previous_checksums[key] != page.checksum
      }
    end

    File.write(
      File.join(@build_root, "manifest.json"),
      JSON.pretty_generate(
        site_name: @site_name,
        base_url: @base_url,
        generated_at: Time.now.utc.iso8601,
        duration_seconds: elapsed(started_at),
        pages: records
      )
    )
  end

  def elapsed(started_at)
    (Time.now - started_at).round(3)
  end
end
