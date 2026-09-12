# frozen_string_literal: true

require "fileutils"
require_relative "../../app/services/s3_deployer"

RSpec.describe S3Deployer do
  class FakeS3Client
    attr_reader :objects

    def initialize
      @objects = []
    end

    def put_object(payload)
      @objects << payload
    end
  end

  class FakeCloudFrontClient
    attr_reader :invalidations

    def initialize
      @invalidations = []
    end

    def create_invalidation(payload)
      @invalidations << payload
    end
  end

  it "uploads files with content type and cache headers" do
    build_dir = ENV.fetch("BUILD_DIR")
    FileUtils.mkdir_p(File.join(build_dir, "assets"))
    File.write(File.join(build_dir, "index.html"), "<h1>Hi</h1>")
    File.write(File.join(build_dir, "assets", "site.css"), "body{}")
    client = FakeS3Client.new

    described_class.new(bucket: "docs", region: "us-east-1", client: client).deploy_directory(build_dir)

    expect(client.objects.map { |object| object[:key] }).to contain_exactly("assets/site.css", "index.html")
    expect(client.objects.find { |object| object[:key] == "index.html" }[:cache_control]).to include("max-age=60")
  end

  it "requests a CloudFront invalidation when configured" do
    build_dir = ENV.fetch("BUILD_DIR")
    FileUtils.mkdir_p(build_dir)
    File.write(File.join(build_dir, "index.html"), "<h1>Hi</h1>")
    cloudfront = FakeCloudFrontClient.new

    described_class.new(
      bucket: "docs",
      region: "us-east-1",
      client: FakeS3Client.new,
      cloudfront_client: cloudfront,
      distribution_id: "DIST123"
    ).deploy_directory(build_dir, invalidate: true, invalidation_paths: ["/index.html"])

    expect(cloudfront.invalidations.first[:distribution_id]).to eq("DIST123")
    expect(cloudfront.invalidations.first[:invalidation_batch][:paths][:items]).to eq(["/index.html"])
  end
end
