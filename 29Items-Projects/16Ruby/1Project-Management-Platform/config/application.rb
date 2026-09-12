require_relative "boot"

require "rails/all"

Bundler.require(*Rails.groups)

module PmPlatform
  class Application < Rails::Application
    config.load_defaults 7.1

    config.time_zone = "UTC"
    config.active_job.queue_adapter = :sidekiq
    config.active_record.encryption.primary_key = ENV.fetch("AR_ENCRYPTION_PRIMARY_KEY", "x" * 32)
    config.active_record.encryption.deterministic_key = ENV.fetch("AR_ENCRYPTION_DETERMINISTIC_KEY", "y" * 32)
    config.active_record.encryption.key_derivation_salt = ENV.fetch("AR_ENCRYPTION_SALT", "z" * 32)

    config.autoload_lib(ignore: %w[assets tasks])

    config.generators do |g|
      g.test_framework :rspec,
                       fixtures: true,
                       view_specs: false,
                       helper_specs: false,
                       routing_specs: false,
                       controller_specs: false,
                       request_specs: true
      g.factory_bot dir: "spec/factories"
    end
  end
end
