required_variables = %w[DATABASE_URL REDIS_URL JWT_SECRET_KEY_BASE SECRET_KEY_BASE]
missing_variables = required_variables.select { |key| ENV[key].blank? }

raise "Missing required environment variables: #{missing_variables.join(', ')}" if missing_variables.any? && !Rails.env.test?

if Rails.env.production?
  placeholder_variables = required_variables.select do |key|
    ENV[key].to_s.match?(/\A(replace-with|change-me|example|test-secret)/i)
  end

  raise "Refusing to boot production with placeholder secrets: #{placeholder_variables.join(', ')}" if placeholder_variables.any?
end
