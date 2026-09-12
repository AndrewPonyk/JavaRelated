from unittest.mock import MagicMock

from src.services.billing_service import BillingService


def test_generate_invoice_within_included_quota() -> None:
    mock_repo = MagicMock()
    mock_repo.get_tenant_metadata.return_value = {
        "tenant_id": "tenant-pro-1",
        "tier": "PRO",
    }
    # Pro tier includes 1,000,000 units. Consumed: 500,000 units.
    mock_repo.get_usage_counter.return_value = {
        "total_count": 500_000,
    }

    service = BillingService(repo=mock_repo)
    invoice = service.generate_invoice_preview("tenant-pro-1", "api_calls")

    assert invoice.tenant_id == "tenant-pro-1"
    assert invoice.tier == "PRO"
    assert invoice.base_charge_cents == 19900  # $199.00
    assert invoice.overage_units == 0
    assert invoice.overage_charge_cents == 0
    assert invoice.total_due_cents == 19900


def test_generate_invoice_with_overage_charge() -> None:
    mock_repo = MagicMock()
    mock_repo.get_tenant_metadata.return_value = {
        "tenant_id": "tenant-starter-1",
        "tier": "STARTER",
    }
    # Starter tier includes 100,000 units. Consumed: 150,000 units (50,000 overage).
    # Overage rate: 0.08 cents per unit -> 50,000 * 0.08 = 4000 cents ($40.00).
    # Base price: 4900 cents ($49.00). Total: 8900 cents ($89.00).
    mock_repo.get_usage_counter.return_value = {
        "total_count": 150_000,
    }

    service = BillingService(repo=mock_repo)
    invoice = service.generate_invoice_preview("tenant-starter-1", "api_calls")

    assert invoice.total_units_consumed == 150_000
    assert invoice.included_units == 100_000
    assert invoice.overage_units == 50_000
    assert invoice.overage_charge_cents == 4000
    assert invoice.total_due_cents == 8900
