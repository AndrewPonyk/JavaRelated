require "simplecov"
SimpleCov.start "rails" do
  enable_coverage :branch
  minimum_coverage 70
  add_filter "/spec/"
  add_filter "/config/"
  add_filter "/bin/"
end

ENV["RAILS_ENV"] ||= "test"
require_relative "spec_helper"
require_relative "../config/environment"
abort("Rails is running in production mode!") if Rails.env.production?

require "rspec/rails"
require "capybara/rspec"
require "webmock/rspec"
require "shoulda/matchers"
require "factory_bot_rails"

Dir[Rails.root.join("spec/support/**/*.rb")].each { |f| require f }

begin
  ActiveRecord::Migration.maintain_test_schema!
rescue ActiveRecord::PendingMigrationError => e
  abort e.to_s.strip
end

Shoulda::Matchers.configure do |config|
  config.integrate do |with|
    with.test_framework :rspec
    with.library :rails
  end
end

RSpec.configure do |config|
  config.fixture_paths = [Rails.root.join("spec/fixtures")]
  config.use_transactional_fixtures = true
  config.infer_spec_type_from_file_location!
  config.filter_rails_from_backtrace!

  config.include FactoryBot::Syntax::Methods
  config.include ActiveJob::TestHelper
end

WebMock.disable_net_connect!(allow_localhost: true)
