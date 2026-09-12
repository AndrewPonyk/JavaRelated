Rails.application.configure do
  config.enable_reloading = true
  config.eager_load = false
  config.consider_all_requests_local = true
  config.server_timing = true
  config.log_level = :debug
  config.active_job.queue_adapter = :sidekiq
end
