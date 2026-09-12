# frozen_string_literal: true

class Rack::Attack
  throttle('api/ip', limit: ENV.fetch('RATE_LIMIT_PER_MINUTE', 120).to_i, period: 1.minute) do |request|
    request.ip if request.path.start_with?('/api/')
  end

  throttle('auth/ip', limit: ENV.fetch('AUTH_RATE_LIMIT_PER_MINUTE', 20).to_i, period: 1.minute) do |request|
    request.ip if request.path.start_with?('/api/v1/auth/')
  end

  self.throttled_responder = lambda do |_request|
    [
      429,
      { 'Content-Type' => 'application/json' },
      [{ error: { code: 'rate_limited', message: 'Too many requests' } }.to_json]
    ]
  end
end

Rails.application.config.middleware.use Rack::Attack
