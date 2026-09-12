return unless ENV["SENTRY_DSN"].present?

Sentry.init do |config|
  config.dsn = ENV.fetch("SENTRY_DSN")
  config.breadcrumbs_logger = %i[active_support_logger http_logger]
  config.traces_sample_rate = 0.1
  config.environment = Rails.env
  config.release = ENV["HEROKU_SLUG_COMMIT"]
  config.send_default_pii = false
end
