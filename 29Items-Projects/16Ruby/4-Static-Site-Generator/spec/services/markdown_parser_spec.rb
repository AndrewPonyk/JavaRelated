# frozen_string_literal: true

require "tempfile"
require_relative "../../app/services/markdown_parser"

RSpec.describe MarkdownParser do
  it "parses front matter and markdown into a page model" do
    file = Tempfile.new(["getting-started", ".md"])
    file.write(<<~MARKDOWN)
      ---
      title: Getting Started
      slug: getting-started
      description: Example page
      ---

      # Hello
    MARKDOWN
    file.close

    page = described_class.new.parse_file(file.path)

    expect(page.title).to eq("Getting Started")
    expect(page.slug).to eq("getting-started")
    expect(page.html).to include("<h1")
    expect(page.keywords).to include("getting")
  ensure
    file&.unlink
  end

  it "rejects invalid front matter" do
    file = Tempfile.new(["bad", ".md"])
    file.write("---\ntitle:\nslug: Bad Slug\n---\n# Bad")
    file.close

    expect { described_class.new.parse_file(file.path) }.to raise_error(ValidationError)
  ensure
    file&.unlink
  end

  it "rejects scalar tags and reports the source path" do
    file = Tempfile.new(["bad-tags", ".md"])
    file.write("---\ntitle: Bad Tags\nslug: bad-tags\ntags: guide\n---\n# Bad")
    file.close

    expect { described_class.new.parse_file(file.path) }
      .to raise_error(ValidationError, /#{Regexp.escape(file.path)}/)
  ensure
    file&.unlink
  end

  it "filters raw HTML from rendered Markdown" do
    file = Tempfile.new(["safe", ".md"])
    file.write(<<~MARKDOWN)
      ---
      title: Safe
      slug: safe
      ---

      # Safe

      <script>alert("xss")</script>
    MARKDOWN
    file.close

    page = described_class.new.parse_file(file.path)

    expect(page.html).not_to include("<script>")
  ensure
    file&.unlink
  end

  it "accepts front matter delimiters with leading whitespace" do
    file = Tempfile.new(["indented", ".md"])
    file.write(<<~MARKDOWN)
        ---
        title: Indented
        slug: indented
        ---

      # Indented
    MARKDOWN
    file.close

    page = described_class.new.parse_file(file.path)

    expect(page.title).to eq("Indented")
  ensure
    file&.unlink
  end
end
