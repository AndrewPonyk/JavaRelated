Devise.setup do |config|
  config.mailer_sender = ENV.fetch("MAILER_FROM", "noreply@example.com")

  require "devise/orm/active_record"

  config.case_insensitive_keys = [:email]
  config.strip_whitespace_keys = [:email]

  config.skip_session_storage = [:http_auth]
  config.stretches = Rails.env.test? ? 1 : 12

  config.reconfirmable = false
  config.expire_all_remember_me_on_sign_out = true
  config.password_length = 8..128
  config.email_regexp = /\A[^@\s]+@[^@\s]+\z/

  config.reset_password_within = 6.hours
  config.sign_in_after_reset_password = true
  config.sign_out_via = :delete

  config.responder = ActionController::Responder

  # Register Google OAuth2 provider. When client credentials are absent (local dev/test),
  # pass empty strings so Devise boots without error; the strategy simply won't authenticate.
  config.omniauth :google_oauth2,
                  ENV.fetch("GOOGLE_OAUTH_CLIENT_ID", ""),
                  ENV.fetch("GOOGLE_OAUTH_CLIENT_SECRET", "")
end
