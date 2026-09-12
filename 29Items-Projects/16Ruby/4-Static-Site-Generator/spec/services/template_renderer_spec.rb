# frozen_string_literal: true

require_relative "../../app/repositories/page_repository"
require_relative "../../app/services/template_renderer"

RSpec.describe TemplateRenderer do
  it "renders a full HTML document" do
    page = PageRepository.new.find(version: "v2", slug: "getting-started")
    html = described_class.new.render_page(page, versions: %w[v2 v1])

    expect(html).to include("<!doctype html>")
    expect(html).to include("Version 2 guide")
    expect(html).to include("/docs/v1/getting-started")
  end
end
