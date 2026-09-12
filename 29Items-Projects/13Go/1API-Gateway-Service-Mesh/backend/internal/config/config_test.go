package config

import "testing"

func TestLoadWithDefaults(t *testing.T) {
	t.Setenv("APP_ENV", "")
	t.Setenv("HTTP_ADDR", "")
	t.Setenv("DATABASE_URL", "postgres://example")
	t.Setenv("ADMIN_API_KEY", "test-key")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if cfg.Environment != "local" {
		t.Fatalf("expected local env, got %q", cfg.Environment)
	}
	if cfg.AdminAPIKey != "test-key" {
		t.Fatalf("expected configured admin api key, got %q", cfg.AdminAPIKey)
	}
}

func TestProductionRequiresStrictAuth(t *testing.T) {
	t.Setenv("APP_ENV", "prod")
	t.Setenv("DATABASE_URL", "postgres://example")
	t.Setenv("ADMIN_API_KEY", "test-key")
	t.Setenv("AUTH_MODE", "permissive")

	if _, err := Load(); err == nil {
		t.Fatal("expected production permissive auth to fail validation")
	}
}
