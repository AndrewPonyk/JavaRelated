# frozen_string_literal: true

require "erb"
require "fileutils"
require "yaml"
require_relative "../errors"

class RedirectManager
  SOURCE_PATTERN = %r{\A/[a-zA-Z0-9._~!$&'()*+,;=:@/-]+\z}

  def initialize(path: File.join("data", "redirects.yml"))
    @path = path
  end

  def redirects
    return {} unless File.exist?(@path)

    payload = YAML.safe_load(File.read(@path), aliases: false) || {}
    raise ValidationError, "Redirect configuration must be a mapping" unless payload.is_a?(Hash)

    payload
  rescue Psych::Exception => e
    raise ValidationError.new("Invalid redirect YAML", details: [e.message])
  end

  def write(build_root)
    redirects.each do |source, destination|
      validate_redirect!(source, destination)
      output_path = safe_output_path(build_root, source)
      FileUtils.mkdir_p(File.dirname(output_path))
      File.write(output_path, redirect_html(destination))
    end
  end

  private

  def validate_redirect!(source, destination)
    errors = []
    source = source.to_s
    destination = destination.to_s

    errors << "redirect source must be an absolute site path" unless source.match?(SOURCE_PATTERN)
    errors << "redirect source cannot contain path traversal" if source.split("/").include?("..")
    unless destination.start_with?("/", "http://", "https://")
      errors << "redirect destination must be a site path or HTTP(S) URL"
    end
    errors << "redirect destination cannot contain control characters" if destination.match?(/[[:cntrl:]]/)

    raise ValidationError.new("Invalid redirect configuration", details: errors) unless errors.empty?
  end

  def safe_output_path(build_root, source)
    root = File.expand_path(build_root)
    path = File.expand_path(File.join(root, source.sub(%r{\A/}, ""), "index.html"))
    return path if path.start_with?("#{root}#{File::SEPARATOR}")

    raise ValidationError.new("Invalid redirect output path", details: [source])
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
end
