# frozen_string_literal: true

require "yaml"

class Configuration
  DEFAULTS = {
    "APP_ENV" => "development",
    "BUILD_DIR" => "build",
    "CONTENT_DIR" => File.join("content", "docs"),
    "DATABASE_PATH" => File.join("data", "site.db"),
    "LOG_LEVEL" => "info",
    "PUBLIC_DIR" => "public",
    "SITE_BASE_URL" => "http://localhost:4567",
    "SITE_NAME" => "Static Site Generator"
  }.freeze

  SETTINGS_PATHS = {
    "BUILD_DIR" => %w[build output_dir],
    "CONTENT_DIR" => %w[build content_dir],
    "DATABASE_PATH" => %w[build database_path],
    "PUBLIC_DIR" => %w[build public_dir],
    "SITE_BASE_URL" => %w[site base_url],
    "SITE_NAME" => %w[site name]
  }.freeze

  def self.fetch(key)
    return ENV[key] if ENV.key?(key)

    config_value(key) || environment_value(key) || DEFAULTS.fetch(key)
  end

  def self.environment
    fetch("APP_ENV")
  end

  def self.test?
    environment == "test"
  end

  def self.settings
    return {} unless File.exist?("config/settings.yml")

    YAML.safe_load(File.read("config/settings.yml"), aliases: false) || {}
  end

  def self.environments
    return {} unless File.exist?("config/environments.yml")

    YAML.safe_load(File.read("config/environments.yml"), aliases: false) || {}
  end

  def self.config_value(key)
    path = SETTINGS_PATHS[key]
    return unless path

    dig(settings, path)
  end

  def self.environment_value(key)
    return unless key == "LOG_LEVEL"

    dig(environments, [environment, "log_level"])
  end

  def self.dig(payload, path)
    path.reduce(payload) do |memo, segment|
      break nil unless memo.is_a?(Hash)

      memo[segment]
    end
  end
end
