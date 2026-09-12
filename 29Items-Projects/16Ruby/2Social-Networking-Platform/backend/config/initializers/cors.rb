require "rack/cors"

Rails.application.config.x.frontend_origin = ENV.fetch("FRONTEND_ORIGIN", "http://localhost:5173")

Rails.application.config.middleware.insert_before 0, Rack::Cors do
  allow do
    origins Rails.application.config.x.frontend_origin
    resource "*",
             headers: :any,
             methods: %i[get post put patch delete options head],
             expose: ["Authorization"]
  end
end
