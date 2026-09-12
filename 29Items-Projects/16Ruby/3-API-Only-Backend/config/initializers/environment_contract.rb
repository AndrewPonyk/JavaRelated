# frozen_string_literal: true

module EnvironmentContract
  PLACEHOLDER_PREFIXES = %w[replace_with your_generated].freeze

  module_function

  def placeholder?(value)
    normalized = value.to_s.strip
    normalized.blank? || PLACEHOLDER_PREFIXES.any? { |prefix| normalized.start_with?(prefix) }
  end
end

if Rails.env.development? || Rails.env.production?
  errors = []
  jwt_secret = ENV['JWT_SECRET_KEY'].to_s

  if EnvironmentContract.placeholder?(jwt_secret)
    errors << 'JWT_SECRET_KEY must be set to a non-placeholder value'
  elsif jwt_secret.length < 32
    errors << 'JWT_SECRET_KEY must be at least 32 characters'
  end

  if ENV['DATABASE_URL'].blank? && EnvironmentContract.placeholder?(ENV['POSTGRES_PASSWORD'])
    errors << 'POSTGRES_PASSWORD must be set unless DATABASE_URL is provided'
  end

  if errors.any?
    raise <<~MESSAGE.squish
      Invalid environment configuration: #{errors.join('; ')}.
      Copy .env.example to .env and replace placeholders before starting Docker Compose.
      If POSTGRES_PASSWORD changed after the Postgres volume was created, reset the local
      database volume with scripts/reset-local-db.ps1 or update the database user password manually.
    MESSAGE
  end
end
