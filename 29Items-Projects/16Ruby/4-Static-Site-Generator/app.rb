# frozen_string_literal: true

require "json"
require "logger"
require "rack/deflater"
require "sinatra/base"

require_relative "app/errors"
require_relative "app/helpers/view_helpers"
require_relative "app/routes/api_routes"
require_relative "app/routes/site_routes"

class StaticSiteGeneratorApp < Sinatra::Base
  helpers ViewHelpers
  use Rack::Deflater

  configure do
    set :root, File.expand_path(__dir__)
    set :public_folder, File.join(root, "public")
    set :views, File.join(root, "templates")
    set :logger, Logger.new($stdout)
    set :show_exceptions, false
    enable :protection
  end

  before do
    headers(
      "X-Content-Type-Options" => "nosniff",
      "X-Frame-Options" => "DENY",
      "Referrer-Policy" => "strict-origin-when-cross-origin",
      "Content-Security-Policy" => [
        "default-src 'self'",
        "script-src 'self'",
        "style-src 'self'",
        "img-src 'self' data:",
        "object-src 'none'",
        "base-uri 'self'",
        "frame-ancestors 'none'"
      ].join("; ")
    )
  end

  register SiteRoutes
  register ApiRoutes

  error AppError do
    content_type :json
    error = env["sinatra.error"]
    status error.status
    error.to_h.to_json
  end

  error StandardError do
    content_type :json
    status 500
    settings.logger.error(
      {
        event: "request_failed",
        error: env["sinatra.error"].class.name,
        message: env["sinatra.error"].message
      }.to_json
    )
    { error: "internal_server_error", message: "Internal server error" }.to_json
  end

  run! if app_file == $PROGRAM_NAME
end
