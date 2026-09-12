package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Environment               string
	HTTPAddr                  string
	DatabaseURL               string
	AdminAPIKey               string
	DefaultRateLimitPerMinute int
	GRPCRouteServiceAddr      string
	JWTSharedSecret           string
	AuthMode                  string
	ReadTimeoutSeconds        int
	WriteTimeoutSeconds       int
	ShutdownTimeoutSeconds    int
	MaxRequestBodyBytes        int64
}

func Load() (Config, error) {
	cfg := Config{
		Environment:               getEnv("APP_ENV", "local"),
		HTTPAddr:                  getEnv("HTTP_ADDR", ":8080"),
		DatabaseURL:               getEnv("DATABASE_URL", ""),
		AdminAPIKey:               getEnv("ADMIN_API_KEY", ""),
		DefaultRateLimitPerMinute: getEnvInt("DEFAULT_RATE_LIMIT_PER_MINUTE", 120),
		GRPCRouteServiceAddr:      getEnv("GRPC_ROUTE_SERVICE_ADDR", "localhost:9090"),
		JWTSharedSecret:           getEnv("JWT_SHARED_SECRET", ""),
		AuthMode:                  getEnv("AUTH_MODE", "permissive"),
		ReadTimeoutSeconds:        getEnvInt("READ_TIMEOUT_SECONDS", 15),
		WriteTimeoutSeconds:       getEnvInt("WRITE_TIMEOUT_SECONDS", 30),
		ShutdownTimeoutSeconds:    getEnvInt("SHUTDOWN_TIMEOUT_SECONDS", 10),
		MaxRequestBodyBytes:        int64(getEnvInt("MAX_REQUEST_BODY_BYTES", 1048576)),
	}
	return cfg, cfg.Validate()
}

func (c Config) Validate() error {
	if strings.TrimSpace(c.HTTPAddr) == "" {
		return errors.New("HTTP_ADDR is required")
	}
	if strings.TrimSpace(c.DatabaseURL) == "" {
		return errors.New("DATABASE_URL is required")
	}
	if strings.TrimSpace(c.AdminAPIKey) == "" {
		return errors.New("ADMIN_API_KEY is required")
	}
	if c.DefaultRateLimitPerMinute <= 0 {
		return fmt.Errorf("DEFAULT_RATE_LIMIT_PER_MINUTE must be positive")
	}
	if c.AuthMode != "permissive" && c.AuthMode != "strict" {
		return fmt.Errorf("AUTH_MODE must be permissive or strict")
	}
	if c.AuthMode == "strict" && strings.TrimSpace(c.JWTSharedSecret) == "" {
		return errors.New("JWT_SHARED_SECRET is required in strict auth mode")
	}
	if (c.Environment == "prod" || c.Environment == "production") && c.AuthMode != "strict" {
		return errors.New("AUTH_MODE=strict is required in production")
	}
	if c.MaxRequestBodyBytes <= 0 {
		return errors.New("MAX_REQUEST_BODY_BYTES must be positive")
	}
	return nil
}

func getEnv(key string, fallback string) string {
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
