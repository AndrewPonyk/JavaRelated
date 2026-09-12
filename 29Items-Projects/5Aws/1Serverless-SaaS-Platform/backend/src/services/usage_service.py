import json
from datetime import datetime, timezone
from typing import Any

import boto3

from src.core.config import get_settings
from src.core.logger import get_logger
from src.db.single_table import SingleTableRepository
from src.models.usage import (
    UsageEventIngestRequest,
    UsageHistoryPoint,
    UsageHistoryResponse,
    UsageMetricsResponse,
)

logger = get_logger("usage-service")
_sqs_client: Any = None


def get_sqs_client() -> Any:
    global _sqs_client
    settings = get_settings()
    if _sqs_client is None:
        _sqs_client = boto3.client("sqs", region_name=settings.aws_region)
    return _sqs_client


class UsageService:
    def __init__(self, repo: SingleTableRepository | None = None):
        self.repo = repo or SingleTableRepository()
        self.settings = get_settings()
        self.sqs = get_sqs_client()

    def enqueue_usage_events(self, tenant_id: str, events: list[UsageEventIngestRequest]) -> int:
        """Buffers usage events into Amazon SQS for asynchronous batch aggregation,
        or processes synchronously if SQS is unconfigured or in local development."""
        queue_url = self.settings.usage_queue_url

        if not queue_url:
            logger.info("Direct synchronous ingestion processing (no SQS queue URL configured)", tenant_id=tenant_id)
            current_month = datetime.now(timezone.utc).strftime("%Y-%m")
            tenant = self.repo.get_tenant_metadata(tenant_id)
            quota = int(tenant.get("monthly_quota", 100_000))
            for evt in events:
                self.repo.increment_usage_counter(
                    tenant_id, evt.metric, current_month, evt.count, quota
                )
                self.repo.record_raw_event(
                    tenant_id,
                    evt.idempotency_key,
                    {"metric": evt.metric, "count": evt.count, "metadata": evt.metadata},
                )
            return len(events)

        entries = []
        for idx, evt in enumerate(events):
            body = {
                "tenant_id": tenant_id,
                "metric": evt.metric,
                "count": evt.count,
                "idempotency_key": evt.idempotency_key,
                "timestamp": evt.timestamp or datetime.now(timezone.utc).isoformat(),
                "metadata": evt.metadata,
            }
            entries.append(
                {
                    "Id": f"msg_{idx}",
                    "MessageBody": json.dumps(body),
                }
            )

        response = self.sqs.send_message_batch(QueueUrl=queue_url, Entries=entries)
        successful = len(response.get("Successful", []))
        logger.info(
            "Enqueued usage event batch to SQS",
            tenant_id=tenant_id,
            enqueued_count=successful,
        )
        return successful

    def get_current_metrics(self, tenant_id: str, metric: str = "api_calls") -> UsageMetricsResponse:
        """Queries current period usage and calculates percent consumed against quota."""
        now = datetime.now(timezone.utc)
        current_period = now.strftime("%Y-%m")

        counter_item = self.repo.get_usage_counter(tenant_id, metric, current_period)
        tenant = self.repo.get_tenant_metadata(tenant_id)
        quota_limit = int(tenant.get("monthly_quota", 100_000))

        total_count = int(counter_item.get("total_count", 0)) if counter_item else 0
        percent = round((total_count / quota_limit) * 100, 2) if quota_limit > 0 else 0.0

        return UsageMetricsResponse(
            tenant_id=tenant_id,
            metric=metric,
            period=current_period,
            total_count=total_count,
            quota_limit=quota_limit,
            percent_consumed=percent,
            warning_threshold_exceeded=percent >= 80.0,
            last_updated=counter_item.get("updated_at", now.isoformat()) if counter_item else now.isoformat(),
        )

    def get_usage_history(self, tenant_id: str, metric: str = "api_calls", days: int = 30) -> UsageHistoryResponse:
        """Queries historical daily events to construct a time-series graph."""
        items = self.repo.get_usage_history(tenant_id, metric, days)
        points: list[UsageHistoryPoint] = []
        for item in items:
            raw_data = item.get("data", {})
            date_str = item.get("GSI2SK", "").replace("DATE#", "")
            count_val = int(raw_data.get("count", item.get("count", 1)))
            points.append(
                UsageHistoryPoint(
                    date=date_str or item.get("recorded_at", "")[:10],
                    count=count_val,
                    metric=metric,
                )
            )

        return UsageHistoryResponse(
            tenant_id=tenant_id,
            metric=metric,
            points=points,
            total_points=len(points),
        )
