package config

import (
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	AppEnv             string
	HTTPAddr           string
	PublicBaseURL      string
	DatabaseURL        string
	RedisAddr          string
	RedisPassword      string
	RedisDB            int
	CORSAllowedOrigins []string
	AdminAPIKeys       []string
	RateLimitPerMinute int
	LogLevel           slog.Level
}

func Load() Config {
	return Config{
		AppEnv:             getEnv("APP_ENV", "development"),
		HTTPAddr:           getEnv("HTTP_ADDR", ":8080"),
		PublicBaseURL:      strings.TrimRight(getEnv("PUBLIC_BASE_URL", "http://localhost:8080"), "/"),
		DatabaseURL:        getEnv("DATABASE_URL", "postgres://urlshortener:urlshortener@localhost:5432/urlshortener?sslmode=disable"),
		RedisAddr:          getEnv("REDIS_ADDR", "localhost:6379"),
		RedisPassword:      getEnv("REDIS_PASSWORD", ""),
		RedisDB:            getEnvInt("REDIS_DB", 0),
		CORSAllowedOrigins: splitCSV(getEnv("CORS_ALLOWED_ORIGINS", "http://localhost:5173")),
		AdminAPIKeys:       splitCSV(getEnv("ADMIN_API_KEYS", "")),
		RateLimitPerMinute: getEnvInt("RATE_LIMIT_PER_MINUTE", 120),
		LogLevel:           parseLogLevel(getEnv("LOG_LEVEL", "info")),
	}
}

func (c Config) Validate() error {
	var errs []error

	if strings.TrimSpace(c.DatabaseURL) == "" {
		errs = append(errs, errors.New("DATABASE_URL is required"))
	}
	if strings.TrimSpace(c.RedisAddr) == "" {
		errs = append(errs, errors.New("REDIS_ADDR is required"))
	}
	if strings.TrimSpace(c.HTTPAddr) == "" {
		errs = append(errs, errors.New("HTTP_ADDR is required"))
	}
	if c.RateLimitPerMinute < 0 {
		errs = append(errs, errors.New("RATE_LIMIT_PER_MINUTE cannot be negative"))
	}
	if _, err := url.ParseRequestURI(c.PublicBaseURL); err != nil {
		errs = append(errs, fmt.Errorf("PUBLIC_BASE_URL is invalid: %w", err))
	}
	if c.IsProduction() {
		if len(c.AdminAPIKeys) == 0 {
			errs = append(errs, errors.New("ADMIN_API_KEYS is required in production"))
		}
		if len(c.CORSAllowedOrigins) == 0 {
			errs = append(errs, errors.New("CORS_ALLOWED_ORIGINS is required in production"))
		}
		for _, origin := range c.CORSAllowedOrigins {
			if origin == "*" {
				errs = append(errs, errors.New("wildcard CORS is not allowed in production"))
			}
		}
	}

	return errors.Join(errs...)
}

func (c Config) IsProduction() bool {
	return strings.EqualFold(c.AppEnv, "production")
}

func getEnv(key, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(key)); value != "" {
		return value
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func splitCSV(value string) []string {
	parts := strings.Split(value, ",")
	out := make([]string, 0, len(parts))
	for _, part := range parts {
		trimmed := strings.TrimSpace(part)
		if trimmed != "" {
			out = append(out, trimmed)
		}
	}
	return out
}

func parseLogLevel(value string) slog.Level {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "debug":
		return slog.LevelDebug
	case "warn":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
