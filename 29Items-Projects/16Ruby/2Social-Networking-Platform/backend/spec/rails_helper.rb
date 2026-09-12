require "spec_helper"
ENV["RAILS_ENV"] ||= "test"
ENV["JWT_SECRET_KEY_BASE"] ||= "test-secret"

require_relative "../config/environment"
require "rspec/rails"

Dir[Rails.root.join("spec/support/**/*.rb")].sort.each { |file| require file }

RSpec.configure do |config|
  config.use_transactional_fixtures = true
  config.include ActiveJob::TestHelper
  config.include FactoryBot::Syntax::Methods
  config.include RequestHelpers, type: :request

  config.before(:suite) do
    [ToxicityResult, Notification, Message, Follow, Post, User].each(&:delete_all)
  end
end
