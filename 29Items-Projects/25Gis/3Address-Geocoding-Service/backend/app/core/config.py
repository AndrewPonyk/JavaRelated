from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = Field(default="development", alias="APP_ENV")
    app_name: str = Field(default="Address Geocoding Service", alias="APP_NAME")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")
    api_cors_origins: str = Field(default="http://localhost:5173", alias="API_CORS_ORIGINS")
    allowed_hosts_value: str = Field(default="*", alias="ALLOWED_HOSTS")
    database_url: str = Field(
        default="postgresql+asyncpg://geocoder:geocoder@localhost:5432/geocoder",
        alias="DATABASE_URL",
    )
    nominatim_base_url: str = Field(
        default="https://nominatim.openstreetmap.org",
        alias="NOMINATIM_BASE_URL",
    )
    nominatim_user_agent: str = Field(
        default="address-geocoding-service-dev",
        alias="NOMINATIM_USER_AGENT",
    )
    nominatim_timeout_seconds: float = Field(default=5.0, alias="NOMINATIM_TIMEOUT_SECONDS")
    nominatim_cache_ttl_seconds: float = Field(default=300.0, alias="NOMINATIM_CACHE_TTL_SECONDS")
    # How far (metres) a reverse lookup will search the local address store before
    # optionally falling back to the upstream provider.
    reverse_search_radius_meters: float = Field(
        default=2000.0, alias="REVERSE_SEARCH_RADIUS_METERS"
    )
    # Maximum candidates returned by forward lookup and local search.
    geocode_result_limit: int = Field(default=5, alias="GEOCODE_RESULT_LIMIT")
    # When True, forward/reverse lookups may call Nominatim; when False the service
    # only answers from its own PostGIS store (useful for offline/test environments).
    enable_upstream_geocoder: bool = Field(default=True, alias="ENABLE_UPSTREAM_GEOCODER")

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.api_cors_origins.split(",") if origin.strip()]

    @property
    def allowed_hosts(self) -> list[str]:
        return [host.strip() for host in self.allowed_hosts_value.split(",") if host.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
