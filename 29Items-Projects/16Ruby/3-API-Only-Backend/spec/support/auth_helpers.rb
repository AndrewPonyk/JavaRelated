# frozen_string_literal: true

module AuthHelpers
  def auth_headers(user)
    token = AuthTokenService.issue(user).fetch(:token)
    { 'Authorization' => "Bearer #{token}", 'Content-Type' => 'application/json' }
  end

  def json
    JSON.parse(response.body)
  end
end

RSpec.configure do |config|
  config.include AuthHelpers, type: :request
end
