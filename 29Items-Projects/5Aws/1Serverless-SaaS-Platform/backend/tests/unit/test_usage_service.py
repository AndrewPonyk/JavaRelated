from unittest.mock import MagicMock

from src.models.usage import UsageEventIngestRequest
from src.services.usage_service import UsageService


def test_enqueue_usage_events_via_sqs() -> None:
    # Arrange
    mock_repo = MagicMock()
    service = UsageService(repo=mock_repo)
    service.settings.usage_queue_url = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue"

    mock_sqs = MagicMock()
    mock_sqs.send_message_batch.return_value = {"Successful": [{"Id": "msg_0"}]}
    service.sqs = mock_sqs

    events = [
        UsageEventIngestRequest(
            metric="api_calls",
            count=5,
            idempotency_key="uuid-12345",
        )
    ]

    # Act
    count = service.enqueue_usage_events("tenant-123", events)

    # Assert
    assert count == 1
    mock_sqs.send_message_batch.assert_called_once()


def test_get_current_metrics_calculation() -> None:
    # Arrange
    mock_repo = MagicMock()
    mock_repo.get_tenant_metadata.return_value = {
        "tenant_id": "tenant-123",
        "monthly_quota": 100_000,
    }
    mock_repo.get_usage_counter.return_value = {
        "total_count": 85_000,
        "updated_at": "2026-09-01T00:00:00Z",
    }

    service = UsageService(repo=mock_repo)

    # Act
    metrics = service.get_current_metrics("tenant-123", "api_calls")

    # Assert
    assert metrics.tenant_id == "tenant-123"
    assert metrics.total_count == 85_000
    assert metrics.quota_limit == 100_000
    assert metrics.percent_consumed == 85.0
    assert metrics.warning_threshold_exceeded is True
