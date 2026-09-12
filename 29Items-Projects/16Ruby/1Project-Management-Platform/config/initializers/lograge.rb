Rails.application.configure do
  config.lograge.enabled = !Rails.env.test?
  config.lograge.formatter = Lograge::Formatters::Json.new
  config.lograge.custom_options = lambda do |event|
    {
      request_id: event.payload[:request_id],
      user_id: event.payload[:user_id],
      params: event.payload[:params]&.except("controller", "action", "format", "authenticity_token"),
      env: Rails.env
    }
  end
end
