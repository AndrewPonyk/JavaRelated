# frozen_string_literal: true

Page = Struct.new(
  :title,
  :slug,
  :version,
  :source_path,
  :output_path,
  :front_matter,
  :markdown,
  :html,
  :keywords,
  :checksum,
  keyword_init: true
) do
  def description
    front_matter["description"].to_s
  end

  def canonical_url
    front_matter["canonical_url"].to_s
  end

  def tags
    Array(front_matter["tags"]).map(&:to_s)
  end

  def to_h
    {
      title: title,
      slug: slug,
      version: version,
      source_path: source_path,
      output_path: output_path,
      description: description,
      canonical_url: canonical_url,
      tags: tags,
      front_matter: front_matter,
      keywords: keywords || [],
      checksum: checksum
    }
  end
end
