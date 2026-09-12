# frozen_string_literal: true

require "aws-sdk-cloudfront"
require "aws-sdk-s3"
require "pathname"
require "time"

class S3Deployer
  CACHE_CONTROL = {
    html: "public, max-age=60",
    json: "public, max-age=300",
    asset: "public, max-age=31536000, immutable"
  }.freeze

  DEFAULT_INVALIDATION_PATHS = ["/index.html", "/docs/*", "/search/*", "/versions.json"].freeze

  def initialize(
    bucket:,
    region:,
    client: Aws::S3::Client.new(region: region),
    cloudfront_client: nil,
    distribution_id: nil
  )
    @bucket = bucket
    @region = region
    @client = client
    @cloudfront_client = cloudfront_client
    @distribution_id = distribution_id
  end

  def deploy_directory(build_root, invalidate: false, invalidation_paths: DEFAULT_INVALIDATION_PATHS)
    Dir.glob(File.join(build_root, "**", "*")).select { |path| File.file?(path) }.each do |path|
      key = Pathname.new(path).relative_path_from(Pathname.new(build_root)).to_s.tr("\\", "/")
      upload_file(path, key: key)
    end
    invalidate!(invalidation_paths) if invalidate
  end

  def upload_file(path, key:)
    File.open(path, "rb") do |file|
      @client.put_object(
        bucket: @bucket,
        key: key,
        body: file,
        content_type: content_type_for(path),
        cache_control: cache_control_for(path)
      )
    end
  end

  def invalidate!(paths = DEFAULT_INVALIDATION_PATHS)
    return unless @distribution_id && !@distribution_id.empty?

    cloudfront_client.create_invalidation(
      distribution_id: @distribution_id,
      invalidation_batch: {
        caller_reference: "static-site-generator-#{Time.now.to_i}",
        paths: {
          quantity: paths.size,
          items: paths
        }
      }
    )
  end

  private

  def cloudfront_client
    @cloudfront_client ||= Aws::CloudFront::Client.new(region: @region)
  end

  def content_type_for(path)
    case File.extname(path)
    when ".html" then "text/html; charset=utf-8"
    when ".json" then "application/json; charset=utf-8"
    when ".css" then "text/css; charset=utf-8"
    when ".js" then "application/javascript; charset=utf-8"
    else "application/octet-stream"
    end
  end

  def cache_control_for(path)
    return CACHE_CONTROL[:html] if File.extname(path) == ".html"
    return CACHE_CONTROL[:json] if File.extname(path) == ".json"

    CACHE_CONTROL[:asset]
  end
end
