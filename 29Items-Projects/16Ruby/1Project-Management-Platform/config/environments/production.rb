require "active_support/core_ext/integer/time"

Rails.application.configure do
  config.cache_classes = true
  config.eager_load = true

  config.consider_all_requests_local = false
  config.action_controller.perform_caching = true

  config.public_file_server.enabled = ENV["RAILS_SERVE_STATIC_FILES"].present?
  config.assume_ssl = true
  config.force_ssl = ENV.fetch("FORCE_SSL", "true") == "true"

  config.log_tags = [:request_id]
  config.logger = ActiveSupport::TaggedLogging.new(Logger.new($stdout))
  config.log_level = ENV.fetch("RAILS_LOG_LEVEL", "info").to_sym

  config.cache_store = :redis_cache_store, {
    url: ENV.fetch("REDIS_CACHE_URL") { ENV.fetch("REDIS_URL") },
    error_handler: ->(method:, returning:, exception:) {
      Sentry.capture_exception(exception, extra: { method: method, returning: returning })
    }
  }

  config.active_job.queue_adapter = :sidekiq

  config.action_mailer.delivery_method = :smtp
  config.action_mailer.smtp_settings = {
    address: ENV.fetch("SMTP_HOST"),
    port: ENV.fetch("SMTP_PORT", 587).to_i,
    user_name: ENV.fetch("SMTP_USERNAME"),
    password: ENV.fetch("SMTP_PASSWORD"),
    authentication: :plain,
    enable_starttls_auto: true
  }
  config.action_mailer.default_url_options = { host: ENV.fetch("APPLICATION_HOST"), protocol: "https" }
  config.action_mailer.perform_caching = false

  config.i18n.fallbacks = true
  config.active_support.report_deprecations = false

  config.active_record.dump_schema_after_migration = false
end
