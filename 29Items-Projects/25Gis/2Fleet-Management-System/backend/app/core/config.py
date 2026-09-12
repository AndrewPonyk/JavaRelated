from functools import lru_cache

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    environment: str = "development"
    log_level: str = "INFO"
    api_host: str = "0.0.0.0"
    api_port: int = 8000
    cors_origins_raw: str = Field("http://localhost:5173", alias="CORS_ORIGINS")
    database_url: str = "sqlite:///./fleet.db"
    redis_url: str = "redis://localhost:6379/0"
    kafka_bootstrap_servers: str = "localhost:29092"
    jwt_issuer: str = "https://auth.example.com/"
    jwt_audience: str = "fleet-api"
    jwt_secret: str = ""
    auth_required: bool = False
    auto_create_tables: bool = True
    request_id_header: str = "X-Request-ID"
    active_vehicle_cache_ttl_seconds: int = 60
    enable_security_headers: bool = True
    gzip_minimum_size: int = 1000

    model_config = SettingsConfigDict(env_file=".env", extra="ignore", populate_by_name=True)

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins_raw.split(",") if origin.strip()]

    @model_validator(mode="after")
    def validate_security_settings(self) -> "Settings":
        if self.auth_required and len(self.jwt_secret) < 32:
            raise ValueError("JWT_SECRET must be at least 32 characters when AUTH_REQUIRED=true")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
