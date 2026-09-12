"""
SQS Batch Event Handler for Metered Usage Ingestion.
Consumes messages from Amazon SQS, executes atomic DynamoDB increments,
and emits threshold alert events to Amazon EventBridge.
"""

import json
from datetime import datetime, timezone
from typing import Any

from src.core.logger import get_logger
from src.db.single_table import SingleTableRepository
from src.events.publisher import publish_domain_event

logger = get_logger("usage-sqs-consumer")
_repo = SingleTableRepository()


def handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """
    SQS Event Source Mapping handler.
    Processes batches of up to 10 usage messages.
    """
    records = event.get("Records", [])
    logger.info("Processing SQS usage batch", record_count=len(records))

    now = datetime.now(timezone.utc)
    current_period = now.strftime("%Y-%m")
    failed_record_ids: list[str] = []

    for record in records:
        message_id = record.get("messageId", "")
        try:
            body = json.loads(record.get("body", "{}"))
            tenant_id = body["tenant_id"]
            metric = body.get("metric", "api_calls")
            count = int(body.get("count", 1))
            idempotency_key = body.get("idempotency_key", message_id)

            # 1. Fetch tenant metadata to obtain quota
            tenant = _repo.get_tenant_metadata(tenant_id)
            quota_limit = int(tenant.get("monthly_quota", 100_000))

            # 2. Increment DynamoDB atomic counter
            updated_attrs = _repo.increment_usage_counter(
                tenant_id=tenant_id,
                metric=metric,
                period=current_period,
                increment_by=count,
                quota_limit=quota_limit,
            )

            # 3. Store raw event for audit & idempotency tracking
            _repo.record_raw_event(
                tenant_id=tenant_id,
                event_id=idempotency_key,
                event_data=body,
            )

            # 4. Check threshold warnings (80% and 100%)
            total_consumed = int(updated_attrs.get("total_count", 0))
            percent = (total_consumed / quota_limit) * 100 if quota_limit > 0 else 0

            if percent >= 80.0 and not updated_attrs.get("warning_sent", False):
                logger.warning(
                    "Usage quota threshold exceeded",
                    tenant_id=tenant_id,
                    consumed=total_consumed,
                    quota=quota_limit,
                    percent=percent,
                )
                publish_domain_event(
                    detail_type="UsageThresholdExceeded",
                    detail={
                        "tenant_id": tenant_id,
                        "metric": metric,
                        "period": current_period,
                        "total_count": total_consumed,
                        "quota_limit": quota_limit,
                        "percent_consumed": percent,
                        "severity": "CRITICAL" if percent >= 100.0 else "WARNING",
                    },
                    source="saas.billing",
                )

        except Exception as e:
            logger.exception("Failed processing message record", message_id=message_id, error=str(e))
            failed_record_ids.append(message_id)

    # Return partial batch failure response for SQS redrive
    return {
        "batchItemFailures": [{"itemIdentifier": mid} for mid in failed_record_ids]
    }
