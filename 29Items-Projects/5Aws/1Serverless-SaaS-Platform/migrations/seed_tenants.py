"""
Database Seeding & Auto-Initialization Script for Serverless SaaS Platform.
Ensures DynamoDB table and GSIs exist, then populates initial tenant metadata,
tenant users, billing records, historical usage logs, and sample predictions.
"""

import os
import sys
from datetime import datetime, timezone, timedelta
import boto3
from botocore.exceptions import ClientError

TABLE_NAME = os.environ.get("DYNAMODB_TABLE_NAME", "saas_platform_core_dev")
REGION = os.environ.get("AWS_REGION", "us-east-1")
ENDPOINT_URL = os.environ.get("DYNAMODB_ENDPOINT_URL")

SAMPLE_TENANTS = [
    {
        "tenant_id": "tenant-alpha-enterprise",
        "name": "Alpha Corp International",
        "tier": "ENTERPRISE",
        "status": "ACTIVE",
        "allowed_countries": ["US", "CA", "GB", "DE"],
        "monthly_quota": 10_000_000,
        "current_month_usage": 3_450_200,
        "contact_email": "admin@alphacorp.example.com",
    },
    {
        "tenant_id": "tenant-beta-growth",
        "name": "Beta Growth Innovations",
        "tier": "PRO",
        "status": "ACTIVE",
        "allowed_countries": ["US", "DE", "FR"],
        "monthly_quota": 1_000_000,
        "current_month_usage": 845_000,
        "contact_email": "ops@betagrowth.example.com",
    },
    {
        "tenant_id": "tenant-gamma-restricted",
        "name": "Gamma Financial Services",
        "tier": "STARTER",
        "status": "ACTIVE",
        "allowed_countries": ["US"],
        "monthly_quota": 100_000,
        "current_month_usage": 95_400,
        "contact_email": "security@gammafin.example.com",
    },
]


def ensure_table_exists(dynamodb_client: boto3.client) -> None:
    """Creates single-table if running in DynamoDB Local or unprovisioned account."""
    try:
        dynamodb_client.describe_table(TableName=TABLE_NAME)
        print(f"Table '{TABLE_NAME}' already exists.")
    except ClientError as e:
        if e.response["Error"]["Code"] == "ResourceNotFoundException":
            print(f"Table '{TABLE_NAME}' not found. Creating table with GSIs...")
            dynamodb_client.create_table(
                TableName=TABLE_NAME,
                KeySchema=[
                    {"AttributeName": "PK", "KeyType": "HASH"},
                    {"AttributeName": "SK", "KeyType": "RANGE"},
                ],
                AttributeDefinitions=[
                    {"AttributeName": "PK", "AttributeType": "S"},
                    {"AttributeName": "SK", "AttributeType": "S"},
                    {"AttributeName": "GSI1PK", "AttributeType": "S"},
                    {"AttributeName": "GSI1SK", "AttributeType": "S"},
                    {"AttributeName": "GSI2PK", "AttributeType": "S"},
                    {"AttributeName": "GSI2SK", "AttributeType": "S"},
                ],
                GlobalSecondaryIndexes=[
                    {
                        "IndexName": "GSI1",
                        "KeySchema": [
                            {"AttributeName": "GSI1PK", "KeyType": "HASH"},
                            {"AttributeName": "GSI1SK", "KeyType": "RANGE"},
                        ],
                        "Projection": {"ProjectionType": "ALL"},
                    },
                    {
                        "IndexName": "GSI2",
                        "KeySchema": [
                            {"AttributeName": "GSI2PK", "KeyType": "HASH"},
                            {"AttributeName": "GSI2SK", "KeyType": "RANGE"},
                        ],
                        "Projection": {"ProjectionType": "ALL"},
                    },
                ],
                BillingMode="PAY_PER_REQUEST",
            )
            print(f"Created table '{TABLE_NAME}'.")
        else:
            raise


def seed_database() -> None:
    kwargs = {"region_name": REGION}
    if ENDPOINT_URL:
        kwargs["endpoint_url"] = ENDPOINT_URL
        kwargs["aws_access_key_id"] = os.environ.get("AWS_ACCESS_KEY_ID", "local")
        kwargs["aws_secret_access_key"] = os.environ.get("AWS_SECRET_ACCESS_KEY", "local")

    ddb_client = boto3.client("dynamodb", **kwargs)
    ensure_table_exists(ddb_client)

    dynamodb = boto3.resource("dynamodb", **kwargs)
    table = dynamodb.Table(TABLE_NAME)

    now = datetime.now(timezone.utc)
    current_month_str = now.strftime("%Y-%m")

    for tenant in SAMPLE_TENANTS:
        tid = tenant["tenant_id"]
        print(f"Seeding tenant: {tid} ({tenant['name']})...")

        # 1. Put Tenant Metadata
        metadata_item = {
            "PK": f"TENANT#{tid}",
            "SK": "METADATA",
            "GSI1PK": f"STATUS#{tenant['status']}",
            "GSI1SK": f"PLAN#{tenant['tier']}",
            "tenant_id": tid,
            "name": tenant["name"],
            "tier": tenant["tier"],
            "status": tenant["status"],
            "allowed_countries": tenant["allowed_countries"],
            "monthly_quota": tenant["monthly_quota"],
            "contact_email": tenant["contact_email"],
            "created_at": now.isoformat(),
            "updated_at": now.isoformat(),
        }
        table.put_item(Item=metadata_item)

        # 2. Put Tenant User
        user_item = {
            "PK": f"TENANT#{tid}",
            "SK": f"USER#usr-{tid[:8]}",
            "GSI1PK": "USER#EMAIL",
            "GSI1SK": tenant["contact_email"],
            "tenant_id": tid,
            "user_id": f"usr-{tid[:8]}",
            "email": tenant["contact_email"],
            "name": f"Admin of {tenant['name']}",
            "role": "ADMIN",
            "created_at": now.isoformat(),
        }
        table.put_item(Item=user_item)

        # 3. Put Usage Counter for active period
        usage_item = {
            "PK": f"TENANT#{tid}",
            "SK": f"USAGE#api_calls#{current_month_str}",
            "GSI1PK": "METRIC#api_calls",
            "GSI1SK": f"VAL#{str(tenant['current_month_usage']).zfill(12)}",
            "tenant_id": tid,
            "metric": "api_calls",
            "period": current_month_str,
            "total_count": tenant["current_month_usage"],
            "quota_limit": tenant["monthly_quota"],
            "warning_sent": tenant["current_month_usage"] >= (tenant["monthly_quota"] * 0.8),
            "updated_at": now.isoformat(),
        }
        table.put_item(Item=usage_item)

        # 4. Put Sample Past 7-Day Usage Event Logs for chart visualization
        for i in range(7, 0, -1):
            past_date = (now - timedelta(days=i)).strftime("%Y-%m-%d")
            daily_count = int(tenant["current_month_usage"] // 30 * (0.85 + (i % 3) * 0.1))
            event_item = {
                "PK": f"TENANT#{tid}",
                "SK": f"EVENT#{past_date}T12:00:00Z#seed-{i}",
                "GSI2PK": f"TENANT#{tid}#USAGE",
                "GSI2SK": f"DATE#{past_date}",
                "tenant_id": tid,
                "event_id": f"seed-{tid}-{i}",
                "metric": "api_calls",
                "date": past_date,
                "count": daily_count,
                "recorded_at": f"{past_date}T12:00:00Z",
            }
            table.put_item(Item=event_item)

        # 5. Put Sample Prediction
        prediction_item = {
            "PK": f"TENANT#{tid}",
            "SK": "PREDICTION#api_calls",
            "GSI1PK": "PREDICTION",
            "GSI1SK": now.isoformat(),
            "tenant_id": tid,
            "metric": "api_calls",
            "forecast_7_day": int(tenant["current_month_usage"] * 0.28),
            "forecast_30_day": int(tenant["current_month_usage"] * 1.25),
            "historical_sum": tenant["current_month_usage"],
            "model_version": "deepar-serverless-v2.1",
            "anomaly_risk": "HIGH" if tenant["current_month_usage"] >= (tenant["monthly_quota"] * 0.9) else "LOW",
            "generated_at": now.isoformat(),
        }
        table.put_item(Item=prediction_item)

    print("Database initialization and seeding completed successfully!")


if __name__ == "__main__":
    try:
        seed_database()
    except Exception as e:
        print(f"Error seeding database: {e}", file=sys.stderr)
        sys.exit(1)
