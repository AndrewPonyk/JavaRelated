from unittest.mock import MagicMock, patch

from src.models.tenant import TenantCreateRequest, TenantTier
from src.services.tenant_service import TenantService


@patch("src.services.tenant_service.publish_domain_event")
def test_create_tenant(mock_publish: MagicMock) -> None:
    mock_repo = MagicMock()
    service = TenantService(repo=mock_repo)

    req = TenantCreateRequest(
        name="Acme Global",
        tier=TenantTier.PRO,
        contact_email="billing@acmeglobal.example.com",
        allowed_countries=["US", "GB"],
        monthly_quota=1_000_000,
    )

    response = service.create_tenant(req)

    assert response.name == "Acme Global"
    assert response.tier == TenantTier.PRO
    assert response.contact_email == "billing@acmeglobal.example.com"
    assert response.status == "ACTIVE"
    mock_repo.put_tenant_metadata.assert_called_once()
    mock_publish.assert_called_once()


def test_get_tenant() -> None:
    mock_repo = MagicMock()
    mock_repo.get_tenant_metadata.return_value = {
        "tenant_id": "tenant-xyz-99",
        "name": "Beta Labs",
        "tier": "ENTERPRISE",
        "status": "ACTIVE",
        "contact_email": "ops@betalabs.example.com",
        "allowed_countries": ["US", "DE"],
        "monthly_quota": 10_000_000,
        "created_at": "2026-09-01T00:00:00Z",
        "updated_at": "2026-09-01T00:00:00Z",
    }
    service = TenantService(repo=mock_repo)

    tenant = service.get_tenant("tenant-xyz-99")

    assert tenant.tenant_id == "tenant-xyz-99"
    assert tenant.tier == "ENTERPRISE"
    assert "DE" in tenant.allowed_countries
