package config

import (
	"errors"
	"os"
	"strconv"
	"time"
)

type Config struct {
	AppEnv             string
	HTTPAddr           string
	DatabaseURL        string
	RedisAddr          string
	RedisPassword      string
	NATSURL            string
	APIKey             string
	AutoMigrate        bool
	EnforceHTTPS       bool
	WorkerConcurrency int
	ShutdownTimeout    time.Duration
	MetricsEnabled     bool
}

func Load() Config {
	return Config{
		AppEnv:             getEnv("APP_ENV", "development"),
		HTTPAddr:           getEnv("HTTP_ADDR", ":8080"),
		DatabaseURL:        getEnv("DATABASE_URL", ""),
		RedisAddr:          getEnv("REDIS_ADDR", "localhost:6379"),
		RedisPassword:      getEnv("REDIS_PASSWORD", ""),
		NATSURL:            getEnv("NATS_URL", "nats://localhost:4222"),
		APIKey:             getEnv("API_KEY", ""),
		AutoMigrate:        getEnvBool("AUTO_MIGRATE", true),
		EnforceHTTPS:       getEnvBool("ENFORCE_HTTPS", false),
		WorkerConcurrency:  getEnvInt("WORKER_CONCURRENCY", 8),
		ShutdownTimeout:    time.Duration(getEnvInt("SHUTDOWN_TIMEOUT_SECONDS", 20)) * time.Second,
		MetricsEnabled:     getEnvBool("METRICS_ENABLED", true),
	}
}

func (c Config) ValidateAPI() error {
	if c.APIKey == "" {
		return errors.New("API_KEY is required")
	}
	if c.DatabaseURL == "" {
		return errors.New("DATABASE_URL is required")
	}
	if c.AppEnv == "production" && len(c.APIKey) < 24 {
		return errors.New("API_KEY must be at least 24 characters in production")
	}
	return nil
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getEnvBool(key string, fallback bool) bool {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}
