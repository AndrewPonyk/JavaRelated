# frozen_string_literal: true

require "erb"
require "fileutils"
require "time"

class FeedGenerator
  def initialize(site_name:, base_url:)
    @site_name = site_name
    @base_url = base_url.sub(%r{/\z}, "")
  end

  def write(build_root, pages)
    FileUtils.mkdir_p(build_root)
    File.write(File.join(build_root, "feed.xml"), xml_for(pages))
  end

  private

  def xml_for(pages)
    items = pages.sort_by { |page| File.mtime(page.source_path) }.reverse.first(20).map do |page|
      url = "#{@base_url}/docs/#{page.version}/#{page.slug}"
      <<~ITEM
        <item>
          <title>#{escape(page.title)}</title>
          <link>#{escape(url)}</link>
          <guid>#{escape(url)}</guid>
          <description>#{escape(page.description)}</description>
          <pubDate>#{File.mtime(page.source_path).utc.rfc2822}</pubDate>
        </item>
      ITEM
    end

    <<~XML
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0">
        <channel>
          <title>#{escape(@site_name)}</title>
          <link>#{escape(@base_url)}</link>
          <description>#{escape("#{@site_name} documentation updates")}</description>
          #{items.join}
        </channel>
      </rss>
    XML
  end

  def escape(value)
    ERB::Util.html_escape(value.to_s)
  end
end
