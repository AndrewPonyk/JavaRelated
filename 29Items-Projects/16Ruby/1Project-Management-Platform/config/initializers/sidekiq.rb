require "sidekiq"

redis_url = ENV.fetch("REDIS_SIDEKIQ_URL") { ENV["REDIS_URL"] }

Sidekiq.configure_server do |config|
  config.redis = { url: redis_url }
end

Sidekiq.configure_client do |config|
  config.redis = { url: redis_url }
end

# Protect the Sidekiq Web UI with HTTP basic auth in production.
# Use digest comparison to avoid SecurityUtils length mismatch errors.
if Rails.env.production?
  require "sidekiq/web"
  expected_user_digest = Digest::SHA256.hexdigest(ENV.fetch("SIDEKIQ_WEB_USERNAME", ""))
  expected_pass_digest = Digest::SHA256.hexdigest(ENV.fetch("SIDEKIQ_WEB_PASSWORD", ""))

  Sidekiq::Web.use(Rack::Auth::Basic) do |user, pass|
    ActiveSupport::SecurityUtils.secure_compare(Digest::SHA256.hexdigest(user), expected_user_digest) &
      ActiveSupport::SecurityUtils.secure_compare(Digest::SHA256.hexdigest(pass), expected_pass_digest)
  end
end
