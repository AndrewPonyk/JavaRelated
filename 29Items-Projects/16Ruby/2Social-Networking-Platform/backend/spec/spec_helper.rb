require "simplecov"

SimpleCov.start "rails" do
  enable_coverage :branch
  minimum_coverage 70
  add_filter "/spec/"
end

RSpec.configure do |config|
  config.expect_with :rspec do |expectations|
    expectations.syntax = :expect
  end
end
