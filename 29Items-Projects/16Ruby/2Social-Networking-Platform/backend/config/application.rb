require "rails"
require "active_record/railtie"
require "action_controller/railtie"
require "active_job/railtie"
require "action_mailer/railtie"
require "graphql"
require "net/http"

Bundler.require(*Rails.groups)

module SocialNetworkingPlatform
  class Application < Rails::Application
    config.load_defaults 7.1
    config.api_only = true
    config.active_job.queue_adapter = :sidekiq
    config.middleware.use Rack::Deflater
    config.autoload_paths << Rails.root.join("app/services")
    config.autoload_paths << Rails.root.join("app/serializers")
    config.eager_load_paths << Rails.root.join("app/services")
    config.eager_load_paths << Rails.root.join("app/serializers")

    config.filter_parameters += %i[
      password
      token
      jwt
      secret
      authorization
    ]
  end
end
