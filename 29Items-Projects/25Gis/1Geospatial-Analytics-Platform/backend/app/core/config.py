from functools import lru_cache
from typing import Annotated, Any

from pydantic import Field, field_validator, model_validator
from pydantic.functional_validators import BeforeValidator
from pydantic_settings import BaseSettings, SettingsConfigDict


def parse_csv_or_json_list(value: Any) -> list[str]:
    if value is None or value == "":
        return []
    if isinstance(value, list):
        return [str(item) for item in value]
    if isinstance(value, str):
        stripped = value.strip()
        if stripped.startswith("["):
            import json

            parsed = json.loads(stripped)
            if not isinstance(parsed, list):
                raise ValueError("Expected a JSON list")
            return [str(item) for item in parsed]
        return [item.strip() for item in stripped.split(",") if item.strip()]
    raise ValueError("Expected a list, JSON list, or comma-separated string")


StringList = Annotated[list[str], BeforeValidator(parse_csv_or_json_list)]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "Geospatial Analytics Platform"
    environment: str = "development"
    log_level: str = "INFO"
    cors_origins: StringList = Field(default_factory=lambda: ["http://localhost:5173"])

    database_url: str = "postgresql+psycopg://localhost:5432/geospatial"
    geoserver_public_url: str = "http://localhost:8080/geoserver"
    mapbox_token: str = ""
    jwt_issuer: str = "http://localhost:8000"
    jwt_audience: str = "geospatial-platform"
    jwt_secret: str = ""
    auth_enabled: bool = False
    request_id_header: str = "X-Request-ID"
    ml_model_path: str = "/models/land-use-classifier.joblib"
    worker_concurrency: int = 2
    enforce_https: bool = False

    @field_validator("environment")
    @classmethod
    def normalize_environment(cls, value: str) -> str:
        return value.lower().strip()

    @model_validator(mode="after")
    def validate_security_settings(self) -> "Settings":
        if self.auth_enabled and len(self.jwt_secret) < 32:
            raise ValueError("JWT_SECRET must be at least 32 characters when AUTH_ENABLED=true")
        if self.environment == "production" and not self.enforce_https:
            raise ValueError("ENFORCE_HTTPS must be true in production")
        if self.environment == "production" and "*" in self.cors_origins:
            raise ValueError("Wildcard CORS origins are not allowed in production")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
