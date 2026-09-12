from datetime import datetime, timezone
from typing import Any

from boto3.dynamodb.conditions import Key

from src.core.exceptions import TenantNotFoundException
from src.db.dynamodb_client import get_dynamodb_table


class SingleTableRepository:
    def __init__(self) -> None:
        self.table = get_dynamodb_table()

    # --------------------------------------------------------------------------
    # TENANT ACCESS PATTERNS
    # --------------------------------------------------------------------------
    def get_tenant_metadata(self, tenant_id: str) -> dict[str, Any]:
        """Fetch tenant profile and allowed countries."""
        response = self.table.get_item(
            Key={
                "PK": f"TENANT#{tenant_id}",
                "SK": "METADATA",
            }
        )
        item = response.get("Item")
        if not item:
            raise TenantNotFoundException(tenant_id)
        return item

    def put_tenant_metadata(self, tenant_data: dict[str, Any]) -> dict[str, Any]:
        """Insert or replace tenant metadata."""
        tid = tenant_data["tenant_id"]
        item = {
            "PK": f"TENANT#{tid}",
            "SK": "METADATA",
            "GSI1PK": f"STATUS#{tenant_data.get('status', 'ACTIVE')}",
            "GSI1SK": f"PLAN#{tenant_data.get('tier', 'STARTER')}",
            **tenant_data,
        }
        self.table.put_item(Item=item)
        return item

    def update_tenant_metadata(self, tenant_id: str, updates: dict[str, Any]) -> dict[str, Any]:
        """Dynamically update tenant metadata attributes."""
        # Ensure tenant exists first
        self.get_tenant_metadata(tenant_id)

        now = datetime.now(timezone.utc).isoformat()
        updates["updated_at"] = now

        update_expr_parts = []
        expr_names = {}
        expr_values: dict[str, Any] = {}

        for k, v in updates.items():
            if k in ["PK", "SK", "tenant_id"]:
                continue
            placeholder_name = f"#{k}"
            placeholder_val = f":{k}"
            update_expr_parts.append(f"{placeholder_name} = {placeholder_val}")
            expr_names[placeholder_name] = k
            expr_values[placeholder_val] = v

        if "status" in updates:
            update_expr_parts.append("GSI1PK = :gsi1pk")
            expr_values[":gsi1pk"] = f"STATUS#{updates['status']}"
        if "tier" in updates:
            update_expr_parts.append("GSI1SK = :gsi1sk")
            expr_values[":gsi1sk"] = f"PLAN#{updates['tier']}"

        update_expression = "SET " + ", ".join(update_expr_parts)

        response = self.table.update_item(
            Key={"PK": f"TENANT#{tenant_id}", "SK": "METADATA"},
            UpdateExpression=update_expression,
            ExpressionAttributeNames=expr_names,
            ExpressionAttributeValues=expr_values,
            ReturnValues="ALL_NEW",
        )
        return response.get("Attributes", {})

    def list_tenants(self, status: str = "ACTIVE", limit: int = 50) -> list[dict[str, Any]]:
        """Query GSI1 to retrieve tenants by status."""
        response = self.table.query(
            IndexName="GSI1",
            KeyConditionExpression=Key("GSI1PK").eq(f"STATUS#{status}"),
            Limit=limit,
        )
        return response.get("Items", [])

    # --------------------------------------------------------------------------
    # TENANT USER ACCESS PATTERNS
    # --------------------------------------------------------------------------
    def create_tenant_user(self, tenant_id: str, user_data: dict[str, Any]) -> dict[str, Any]:
        """Create a user record under a tenant."""
        uid = user_data["user_id"]
        now = datetime.now(timezone.utc).isoformat()
        item = {
            "PK": f"TENANT#{tenant_id}",
            "SK": f"USER#{uid}",
            "GSI1PK": "USER#EMAIL",
            "GSI1SK": user_data["email"],
            "tenant_id": tenant_id,
            "created_at": now,
            **user_data,
        }
        self.table.put_item(Item=item)
        return item

    def list_tenant_users(self, tenant_id: str) -> list[dict[str, Any]]:
        """Query all users belonging to a tenant."""
        response = self.table.query(
            KeyConditionExpression=Key("PK").eq(f"TENANT#{tenant_id}") & Key("SK").begins_with("USER#")
        )
        return response.get("Items", [])

    # --------------------------------------------------------------------------
    # USAGE ACCESS PATTERNS
    # --------------------------------------------------------------------------
    def increment_usage_counter(
        self, tenant_id: str, metric: str, period: str, increment_by: int, quota_limit: int
    ) -> dict[str, Any]:
        """Atomically increment a monthly usage counter."""
        now = datetime.now(timezone.utc).isoformat()
        response = self.table.update_item(
            Key={
                "PK": f"TENANT#{tenant_id}",
                "SK": f"USAGE#{metric}#{period}",
            },
            UpdateExpression=(
                "ADD total_count :inc "
                "SET GSI1PK = :gpk, GSI1SK = :now, updated_at = :now, "
                "tenant_id = :tid, metric = :m, period = :p, quota_limit = if_not_exists(quota_limit, :q), "
                "warning_sent = if_not_exists(warning_sent, :f)"
            ),
            ExpressionAttributeValues={
                ":inc": increment_by,
                ":gpk": f"METRIC#{metric}",
                ":now": now,
                ":tid": tenant_id,
                ":m": metric,
                ":p": period,
                ":q": quota_limit,
                ":f": False,
            },
            ReturnValues="ALL_NEW",
        )
        return response.get("Attributes", {})

    def get_usage_counter(self, tenant_id: str, metric: str, period: str) -> dict[str, Any] | None:
        """Fetch current period counter for a tenant."""
        response = self.table.get_item(
            Key={
                "PK": f"TENANT#{tenant_id}",
                "SK": f"USAGE#{metric}#{period}",
            }
        )
        return response.get("Item")

    def record_raw_event(self, tenant_id: str, event_id: str, event_data: dict[str, Any]) -> None:
        """Record raw audit log event with idempotency."""
        now = datetime.now(timezone.utc).isoformat()
        today = now[:10]
        self.table.put_item(
            Item={
                "PK": f"TENANT#{tenant_id}",
                "SK": f"EVENT#{now}#{event_id}",
                "GSI2PK": f"TENANT#{tenant_id}#USAGE",
                "GSI2SK": f"DATE#{today}",
                "tenant_id": tenant_id,
                "event_id": event_id,
                "data": event_data,
                "recorded_at": now,
            }
        )

    def get_usage_history(self, tenant_id: str, metric: str = "api_calls", days: int = 30) -> list[dict[str, Any]]:
        """Query historical daily usage from GSI2."""
        response = self.table.query(
            IndexName="GSI2",
            KeyConditionExpression=Key("GSI2PK").eq(f"TENANT#{tenant_id}#USAGE"),
            Limit=days,
            ScanIndexForward=True,
        )
        return response.get("Items", [])

    # --------------------------------------------------------------------------
    # PREDICTIONS & INVOICES
    # --------------------------------------------------------------------------
    def get_capacity_prediction(self, tenant_id: str, metric: str) -> dict[str, Any] | None:
        """Retrieve latest cached prediction for tenant."""
        response = self.table.get_item(
            Key={
                "PK": f"TENANT#{tenant_id}",
                "SK": f"PREDICTION#{metric}",
            }
        )
        return response.get("Item")

    def put_capacity_prediction(self, tenant_id: str, metric: str, prediction_data: dict[str, Any]) -> None:
        """Cache fresh model forecast in single-table."""
        now = datetime.now(timezone.utc).isoformat()
        item = {
            "PK": f"TENANT#{tenant_id}",
            "SK": f"PREDICTION#{metric}",
            "GSI1PK": "PREDICTION",
            "GSI1SK": now,
            "tenant_id": tenant_id,
            "metric": metric,
            "generated_at": now,
            **prediction_data,
        }
        self.table.put_item(Item=item)

    def list_invoices(self, tenant_id: str) -> list[dict[str, Any]]:
        """List all past generated invoices for tenant."""
        response = self.table.query(
            KeyConditionExpression=Key("PK").eq(f"TENANT#{tenant_id}") & Key("SK").begins_with("INVOICE#")
        )
        return response.get("Items", [])

    def record_invoice(self, tenant_id: str, period: str, invoice_data: dict[str, Any]) -> None:
        """Persist invoice record."""
        now = datetime.now(timezone.utc).isoformat()
        item = {
            "PK": f"TENANT#{tenant_id}",
            "SK": f"INVOICE#{period}",
            "GSI1PK": "INVOICE#STATUS",
            "GSI1SK": invoice_data.get("status", "PAID"),
            "tenant_id": tenant_id,
            "period": period,
            "created_at": now,
            **invoice_data,
        }
        self.table.put_item(Item=item)
