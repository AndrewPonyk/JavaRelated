# frozen_string_literal: true

require "tempfile"
require_relative "../../app/services/redirect_manager"

RSpec.describe RedirectManager do
  it "writes escaped redirect pages inside the build directory" do
    file = Tempfile.new(["redirects", ".yml"])
    file.write("---\n\"/old\": \"/new?from=old\"\n")
    file.close
    build_dir = ENV.fetch("BUILD_DIR")

    described_class.new(path: file.path).write(build_dir)

    html = File.read(File.join(build_dir, "old", "index.html"))
    expect(html).to include("/new?from=old")
  ensure
    file&.unlink
  end

  it "rejects traversal redirect sources" do
    file = Tempfile.new(["redirects", ".yml"])
    file.write("---\n\"/../outside\": \"/new\"\n")
    file.close

    expect { described_class.new(path: file.path).write(ENV.fetch("BUILD_DIR")) }
      .to raise_error(ValidationError, /Invalid redirect configuration/)
  ensure
    file&.unlink
  end
end
