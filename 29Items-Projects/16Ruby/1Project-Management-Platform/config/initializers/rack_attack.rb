Rails.application.config.middleware.use Rack::Attack

class Rack::Attack
  throttle("req/ip", limit: 300, period: 5.minutes) do |req|
    req.ip unless req.path.start_with?("/assets")
  end

  throttle("logins/ip", limit: 5, period: 20.seconds) do |req|
    req.ip if req.path == "/users/sign_in" && req.post?
  end

  throttle("api/token", limit: 600, period: 1.minute) do |req|
    if req.path.start_with?("/api/") && (auth = req.env["HTTP_AUTHORIZATION"]).present?
      auth
    end
  end
end
