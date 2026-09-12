package config

import "testing"

func TestValidateAPIRequiresKey(t *testing.T) {
	cfg := Config{AppEnv: "development", DatabaseURL: "postgres://localhost/db"}
	if err := cfg.ValidateAPI(); err == nil {
		t.Fatalf("expected missing API key error")
	}
}

func TestValidateAPIRequiresLongProductionKey(t *testing.T) {
	cfg := Config{AppEnv: "production", APIKey: "short", DatabaseURL: "postgres://localhost/db"}
	if err := cfg.ValidateAPI(); err == nil {
		t.Fatalf("expected production API key length error")
	}
}

func TestValidateAPIRequiresDatabaseURL(t *testing.T) {
	cfg := Config{AppEnv: "development", APIKey: "local-key"}
	if err := cfg.ValidateAPI(); err == nil {
		t.Fatalf("expected missing database url error")
	}
}

func TestValidateAPIAcceptsDevelopmentKey(t *testing.T) {
	cfg := Config{AppEnv: "development", APIKey: "local-key", DatabaseURL: "postgres://localhost/db"}
	if err := cfg.ValidateAPI(); err != nil {
		t.Fatalf("expected development key to be accepted: %v", err)
	}
}
