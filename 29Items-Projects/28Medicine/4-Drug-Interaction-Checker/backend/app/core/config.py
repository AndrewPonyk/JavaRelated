"""Application configuration via environment variables (pydantic-settings)."""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore", case_sensitive=False
    )

    # ---- Application ----
    app_name: str = "Drug Interaction Checker"
    environment: str = Field(default="development")
    debug: bool = Field(default=False)
    api_v1_prefix: str = "/api/v1"

    # ---- Neo4j ----
    neo4j_uri: str = Field(default="bolt://localhost:7687")
    neo4j_user: str = Field(default="neo4j")
    neo4j_password: str = Field(default="password")
    neo4j_database: str = Field(default="neo4j")
    neo4j_max_pool_size: int = Field(default=50)

    # ---- RxNorm ----
    rxnorm_base_url: str = Field(default="https://rxnav.nlm.nih.gov/REST")
    rxnorm_timeout_seconds: float = Field(default=10.0)
    rxnorm_cache_ttl_seconds: int = Field(default=86_400)
    rxnorm_cache_maxsize: int = Field(default=2048)

    # ---- ML severity service ----
    ml_service_url: str = Field(default="http://localhost:8001")
    ml_enabled: bool = Field(default=True)
    ml_timeout_seconds: float = Field(default=5.0)
    ml_model_artifact_uri: str = Field(default="s3://dic-models/severity/latest")
    ml_model_local_path: str = Field(
        default="", description="Local joblib artifact path; rule-based fallback if empty/missing"
    )
    ml_confidence_floor: float = Field(
        default=0.35,
        description="Min model risk/confidence to surface an ML prediction; "
        "below this it is reported as UNKNOWN (suppresses no-evidence/minor noise)",
    )

    # ---- Security ----
    jwt_secret_key: str = Field(default="change-me-in-prod")
    jwt_algorithm: str = Field(default="HS256")
    access_token_expire_minutes: int = Field(default=30)
    cors_origins: list[str] = Field(default_factory=lambda: ["http://localhost:5173"])
    dev_token_endpoint_enabled: bool = Field(
        default=True, description="Expose POST /auth/token in non-production for testing"
    )

    # ---- Observability ----
    log_level: str = Field(default="INFO")
    log_json: bool = Field(default=True)
    request_id_header: str = Field(default="X-Request-ID")

    # ---- Safety limits ----
    max_drugs_per_check: int = Field(default=50)
    max_pairs_for_ml: int = Field(default=200)
    rate_limit_per_minute: int = Field(
        default=0, description="Per-client request cap; 0 disables rate limiting"
    )

    @property
    def is_production(self) -> bool:
        return self.environment.lower() in {"production", "prod"}


@lru_cache
def get_settings() -> Settings:
    """Cached settings accessor (instantiate once per process)."""
    return Settings()
