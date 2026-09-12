from unittest.mock import MagicMock, patch

import pytest

from src.core.exceptions import TenantNotFoundException
from src.db.single_table import SingleTableRepository


def test_get_tenant_metadata_found() -> None:
    with patch("src.db.single_table.get_dynamodb_table") as mock_get_table:
        mock_table = MagicMock()
        mock_table.get_item.return_value = {
            "Item": {
                "PK": "TENANT#tenant-1",
                "SK": "METADATA",
                "name": "Test Tenant",
                "tier": "STARTER",
            }
        }
        mock_get_table.return_value = mock_table

        repo = SingleTableRepository()
        item = repo.get_tenant_metadata("tenant-1")

        assert item["name"] == "Test Tenant"
        mock_table.get_item.assert_called_once_with(
            Key={"PK": "TENANT#tenant-1", "SK": "METADATA"}
        )


def test_get_tenant_metadata_not_found() -> None:
    with patch("src.db.single_table.get_dynamodb_table") as mock_get_table:
        mock_table = MagicMock()
        mock_table.get_item.return_value = {}
        mock_get_table.return_value = mock_table

        repo = SingleTableRepository()
        with pytest.raises(TenantNotFoundException):
            repo.get_tenant_metadata("non-existent")


def test_put_tenant_metadata() -> None:
    with patch("src.db.single_table.get_dynamodb_table") as mock_get_table:
        mock_table = MagicMock()
        mock_get_table.return_value = mock_table

        repo = SingleTableRepository()
        tenant_data = {
            "tenant_id": "tenant-new",
            "name": "New Corp",
            "tier": "PRO",
            "status": "ACTIVE",
        }
        item = repo.put_tenant_metadata(tenant_data)

        assert item["PK"] == "TENANT#tenant-new"
        assert item["SK"] == "METADATA"
        assert item["GSI1PK"] == "STATUS#ACTIVE"
        mock_table.put_item.assert_called_once()


def test_increment_usage_counter() -> None:
    with patch("src.db.single_table.get_dynamodb_table") as mock_get_table:
        mock_table = MagicMock()
        mock_table.update_item.return_value = {
            "Attributes": {"total_count": 50, "quota_limit": 1000}
        }
        mock_get_table.return_value = mock_table

        repo = SingleTableRepository()
        attrs = repo.increment_usage_counter("tenant-1", "api_calls", "2026-09", 10, 1000)

        assert attrs["total_count"] == 50
        mock_table.update_item.assert_called_once()


def test_record_raw_event() -> None:
    with patch("src.db.single_table.get_dynamodb_table") as mock_get_table:
        mock_table = MagicMock()
        mock_get_table.return_value = mock_table

        repo = SingleTableRepository()
        repo.record_raw_event("tenant-1", "evt-123", {"count": 1})
        mock_table.put_item.assert_called_once()
