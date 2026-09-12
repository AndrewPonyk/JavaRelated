# frozen_string_literal: true

require "erb"
require "fileutils"
require "time"

class SitemapGenerator
  def initialize(base_url:)
    @base_url = base_url.sub(%r{/\z}, "")
  end

  def write(build_root, pages)
    FileUtils.mkdir_p(build_root)
    File.write(File.join(build_root, "sitemap.xml"), xml_for(pages))
  end

  private

  def xml_for(pages)
    urls = pages.map do |page|
      {
        loc: "#{@base_url}/docs/#{page.version}/#{page.slug}",
        lastmod: File.mtime(page.source_path).utc.iso8601
      }
    end

    entries = urls.map do |url|
      "  <url><loc>#{ERB::Util.html_escape(url[:loc])}</loc><lastmod>#{url[:lastmod]}</lastmod></url>"
    end

    <<~XML
      <?xml version="1.0" encoding="UTF-8"?>
      <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
      #{entries.join("\n")}
      </urlset>
    XML
  end
end
