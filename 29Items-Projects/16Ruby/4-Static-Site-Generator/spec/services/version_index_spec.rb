# frozen_string_literal: true

require_relative "../../app/services/version_index"

RSpec.describe VersionIndex do
  it "discovers latest version first" do
    index = described_class.new(content_root: ENV.fetch("CONTENT_DIR"))

    expect(index.discover).to eq(%w[v2 v1])
    expect(index.latest).to eq("v2")
    expect(index.to_h).to eq(latest: "v2", versions: %w[v2 v1])
  end
end
