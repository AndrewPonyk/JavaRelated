# frozen_string_literal: true

require "set"
require_relative "../errors"

class LinkValidator
  HREF_PATTERN = /href=["'](?<href>[^"']+)["']/

  def validate!(build_root)
    html_files = Dir.glob(File.join(build_root, "**", "*.html"))
    generated_paths = html_files.map { |path| generated_path(build_root, path) }.to_set
    generated_paths << "/"
    errors = []

    html_files.each do |file|
      File.read(file).scan(HREF_PATTERN).flatten.each do |href|
        next if external?(href) || anchor?(href)

        normalized = normalize(href)
        if asset?(normalized)
          errors << "#{generated_path(build_root, file)} links to missing asset #{href}" unless asset_exists?(
            build_root,
            normalized
          )
          next
        end

        next if generated_paths.include?(normalized) || generated_paths.include?("#{normalized}.html")

        errors << "#{generated_path(build_root, file)} links to missing #{href}"
      end
    end

    raise ValidationError.new("Broken links detected", details: errors) unless errors.empty?

    true
  end

  private

  def generated_path(build_root, path)
    generated = "/" + path.delete_prefix(build_root).tr("\\", "/").sub(%r{\A/+}, "")
    generated = generated.sub(/index\.html\z/, "")
    generated == "/" ? generated : generated.sub(%r{/\z}, "")
  end

  def external?(href)
    href.start_with?("http://", "https://", "mailto:", "tel:")
  end

  def anchor?(href)
    href.start_with?("#")
  end

  def asset?(href)
    href.start_with?("/assets/")
  end

  def asset_exists?(build_root, href)
    File.file?(File.join(build_root, href.sub(%r{\A/}, "")))
  end

  def normalize(href)
    path = href.split("#").first.split("?").first
    return "/" if path == "/"

    path.sub(%r{/$}, "")
  end
end
