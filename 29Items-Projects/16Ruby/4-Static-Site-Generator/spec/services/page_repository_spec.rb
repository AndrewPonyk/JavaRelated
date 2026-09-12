# frozen_string_literal: true

require_relative "../../app/repositories/page_repository"

RSpec.describe PageRepository do
  subject(:repository) { described_class.new }

  it "creates, updates, finds, and deletes a Markdown-backed page with metadata" do
    created = repository.create(
      "title" => "API Guide",
      "slug" => "api-guide",
      "version" => "v2",
      "description" => "API docs",
      "tags" => ["api"],
      "body" => "# API Guide\n\nUse the JSON API."
    )

    expect(created.title).to eq("API Guide")
    expect(File).to exist(created.source_path)
    expect(repository.page_rows.map { |row| row["slug"] }).to include("api-guide")

    updated = repository.update(
      version: "v2",
      slug: "api-guide",
      attributes: { "title" => "API Reference", "body" => "# API Reference\n\nUpdated." }
    )

    expect(updated.title).to eq("API Reference")
    expect(repository.find(version: "v2", slug: "api-guide").html).to include("Updated")

    deleted = repository.delete(version: "v2", slug: "api-guide")

    expect(deleted.slug).to eq("api-guide")
    expect { repository.find(version: "v2", slug: "api-guide") }.to raise_error(NotFoundError)
  end

  it "validates required create attributes" do
    expect { repository.create("title" => "", "slug" => "bad", "version" => "v2", "body" => "") }
      .to raise_error(ValidationError)
  end

  it "finds pages by front matter slug when filename differs" do
    FileUtils.mkdir_p(File.join(ENV.fetch("CONTENT_DIR"), "v3"))
    File.write(
      File.join(ENV.fetch("CONTENT_DIR"), "v3", "custom-file-name.md"),
      <<~MARKDOWN
        ---
        title: Custom Slug
        slug: custom-slug
        ---

        # Custom Slug
      MARKDOWN
    )

    page = repository.find(version: "v3", slug: "custom-slug", sync_metadata: false)

    expect(page.source_path).to end_with("custom-file-name.md")
  end

  it "deduplicates keywords before writing metadata" do
    page = repository.create(
      "title" => "Repeated Keywords",
      "slug" => "repeated-keywords",
      "version" => "v2",
      "body" => "# Repeated\n\nRepeated repeated repeated."
    )
    page.keywords = [page.keywords.first, page.keywords.first]

    expect { repository.send(:upsert_page!, page) }.not_to raise_error
  end
end
