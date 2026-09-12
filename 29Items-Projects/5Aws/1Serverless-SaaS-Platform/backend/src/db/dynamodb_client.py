import os
from typing import Any

import boto3
from botocore.config import Config

from src.core.config import get_settings

_boto_config = Config(
    retries={"max_attempts": 3, "mode": "standard"},
    connect_timeout=5,
    read_timeout=10,
)

_dynamodb_resource: Any = None


def get_dynamodb_table() -> Any:
    global _dynamodb_resource
    settings = get_settings()
    endpoint_url = os.environ.get("DYNAMODB_ENDPOINT_URL") or None
    if _dynamodb_resource is None:
        kwargs: dict[str, Any] = {
            "region_name": settings.aws_region,
            "config": _boto_config,
        }
        if endpoint_url:
            kwargs["endpoint_url"] = endpoint_url
            kwargs["aws_access_key_id"] = os.environ.get("AWS_ACCESS_KEY_ID", "local")
            kwargs["aws_secret_access_key"] = os.environ.get("AWS_SECRET_ACCESS_KEY", "local")
        _dynamodb_resource = boto3.resource("dynamodb", **kwargs)
    return _dynamodb_resource.Table(settings.dynamodb_table_name)
