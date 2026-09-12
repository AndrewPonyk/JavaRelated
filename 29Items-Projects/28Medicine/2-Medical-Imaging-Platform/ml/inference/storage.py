"""Minimal S3/MinIO reader for the ML service.

Uses the same env-var contract as the backend so docker-compose wires it for
free. Targets the S3 API (MinIO locally, S3 in AWS).
"""

from __future__ import annotations

import os

import boto3
from botocore.config import Config


def _client():
    endpoint = os.getenv("S3_ENDPOINT_URL") or None
    return boto3.client(
        "s3",
        endpoint_url=endpoint,
        region_name=os.getenv("S3_REGION", "us-east-1"),
        aws_access_key_id=os.getenv("S3_ACCESS_KEY", "minioadmin"),
        aws_secret_access_key=os.getenv("S3_SECRET_KEY", "minioadmin"),
        config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
    )


def get_object(key: str, bucket: str | None = None) -> bytes:
    bucket = bucket or os.getenv("S3_BUCKET_DICOM", "dicom-archive")
    resp = _client().get_object(Bucket=bucket, Key=key)
    return resp["Body"].read()
