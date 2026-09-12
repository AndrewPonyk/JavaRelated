#!/usr/bin/env ruby
# frozen_string_literal: true

require_relative "../app/services/s3_deployer"

bucket = ENV.fetch("AWS_S3_BUCKET")
region = ENV.fetch("AWS_REGION", "us-east-1")
build_dir = ENV.fetch("BUILD_DIR", "build")
distribution_id = ENV["CLOUDFRONT_DISTRIBUTION_ID"]

deployer = S3Deployer.new(bucket: bucket, region: region, distribution_id: distribution_id)
deployer.deploy_directory(build_dir, invalidate: distribution_id && !distribution_id.empty?)
puts "Uploaded #{build_dir} to s3://#{bucket}."

if distribution_id && !distribution_id.empty?
  puts "Requested CloudFront invalidation for #{distribution_id}."
end
