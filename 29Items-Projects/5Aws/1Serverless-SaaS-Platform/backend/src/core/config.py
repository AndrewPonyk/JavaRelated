import os
from functools import lru_cache

from pydantic import BaseModel, Field


class Settings(BaseModel):
    environment: str = Field(default_factory=lambda: os.environ.get("ENVIRONMENT", "dev"))
    aws_region: str = Field(default_factory=lambda: os.environ.get("AWS_REGION", "us-east-1"))
    dynamodb_table_name: str = Field(
        default_factory=lambda: os.environ.get("DYNAMODB_TABLE_NAME", "saas_platform_core_dev")
    )
    event_bus_name: str = Field(
        default_factory=lambda: os.environ.get("EVENT_BUS_NAME", "saas-platform-eventbus-dev")
    )
    usage_queue_url: str = Field(
        default_factory=lambda: os.environ.get("USAGE_INGESTION_QUEUE_URL", "")
    )
    sagemaker_endpoint_name: str = Field(
        default_factory=lambda: os.environ.get("SAGEMAKER_ENDPOINT_NAME", "saas-capacity-forecast-dev")
    )
    cognito_user_pool_id: str = Field(
        default_factory=lambda: os.environ.get("COGNITO_USER_POOL_ID", "")
    )
    enforce_geolocation: bool = Field(
        default_factory=lambda: os.environ.get("ENFORCE_GEOLOCATION", "true").lower() == "true"
    )
    default_blocked_countries: list[str] = Field(
        default_factory=lambda: [
            c.strip()
            for c in os.environ.get("DEFAULT_BLOCKED_COUNTRIES", "KP,IR,SY,CU").split(",")
            if c.strip()
        ]
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
