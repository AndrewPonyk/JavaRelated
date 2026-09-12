# frozen_string_literal: true

class VersionIndex
  def initialize(content_root: File.join("content", "docs"))
    @content_root = content_root
  end

  def discover
    return [] unless Dir.exist?(@content_root)

    Dir.children(@content_root)
       .select { |entry| File.directory?(File.join(@content_root, entry)) }
       .select { |entry| Dir.glob(File.join(@content_root, entry, "*.md")).any? }
       .sort_by { |version| version_key(version) }
       .reverse
  end

  def latest
    discover.first
  end

  def to_h
    versions = discover
    {
      latest: versions.first,
      versions: versions
    }
  end

  private

  def version_key(version)
    version.sub(/\Av/, "").split(".").map { |part| part.to_i }
  end
end
