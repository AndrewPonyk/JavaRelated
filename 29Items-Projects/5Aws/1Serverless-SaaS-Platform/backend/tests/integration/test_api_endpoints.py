import os
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

# Ensure environment is configured for testing
os.environ["ENVIRONMENT"] = "test"
os.environ["DYNAMODB_TABLE_NAME"] = "saas_platform_core_test"
os.environ["ENFORCE_GEOLOCATION"] = "true"

from src.server import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def mock_db_and_events():
    """Mock database repository in services to ensure fast, isolated integration tests."""
    with patch("src.server.tenant_service.repo") as mock_repo, \
         patch("src.server.usage_service.repo") as mock_usage_repo, \
         patch("src.server.billing_service.repo") as mock_billing_repo, \
         patch("src.server.prediction_service.repo") as mock_pred_repo, \
         patch("src.server.prediction_service.sm_client") as mock_sm, \
         patch("src.services.tenant_service.publish_domain_event"):

        # In-memory tenant store
        tenant_store = {
            "tenant-alpha-enterprise": {
                "tenant_id": "tenant-alpha-enterprise",
                "name": "Alpha Corp",
                "tier": "ENTERPRISE",
                "status": "ACTIVE",
                "allowed_countries": ["US", "CA", "GB"],
                "monthly_quota": 10_000_000,
                "contact_email": "admin@alpha.example.com",
                "created_at": "2026-09-01T00:00:00Z",
                "updated_at": "2026-09-01T00:00:00Z",
            }
        }
        users_store = []
        counter_store = {
            "total_count": 250_000,
            "quota_limit": 10_000_000,
            "updated_at": "2026-09-09T00:00:00Z",
        }

        # Mock repo methods
        def get_tenant(tid):
            if tid in tenant_store:
                return tenant_store[tid]
            from src.core.exceptions import TenantNotFoundException
            raise TenantNotFoundException(tid)

        def put_tenant(data):
            tenant_store[data["tenant_id"]] = data
            return data

        def update_tenant(tid, updates):
            t = get_tenant(tid)
            t.update(updates)
            return t

        def list_tenants(status="ACTIVE"):
            return list(tenant_store.values())

        def create_user(tid, u):
            users_store.append(u)
            return u

        def list_users(tid):
            return users_store

        def get_counter(tid, metric, period):
            return counter_store

        def increment_counter(tid, metric, period, inc, q):
            counter_store["total_count"] += inc
            return counter_store

        def get_history(tid, metric, days):
            return [
                {"data": {"count": 1000}, "recorded_at": "2026-09-08T00:00:00Z", "GSI2SK": "DATE#2026-09-08"},
                {"data": {"count": 1500}, "recorded_at": "2026-09-09T00:00:00Z", "GSI2SK": "DATE#2026-09-09"},
            ]

        def get_pred(tid, metric):
            return None

        def put_pred(tid, metric, data):
            pass

        mock_repo.get_tenant_metadata.side_effect = get_tenant
        mock_repo.put_tenant_metadata.side_effect = put_tenant
        mock_repo.update_tenant_metadata.side_effect = update_tenant
        mock_repo.list_tenants.side_effect = list_tenants
        mock_repo.create_tenant_user.side_effect = create_user
        mock_repo.list_tenant_users.side_effect = list_users

        mock_usage_repo.get_tenant_metadata.side_effect = get_tenant
        mock_usage_repo.get_usage_counter.side_effect = get_counter
        mock_usage_repo.increment_usage_counter.side_effect = increment_counter
        mock_usage_repo.get_usage_history.side_effect = get_history

        mock_billing_repo.get_tenant_metadata.side_effect = get_tenant
        mock_billing_repo.get_usage_counter.side_effect = get_counter
        mock_billing_repo.list_invoices.return_value = []

        mock_pred_repo.get_capacity_prediction.side_effect = get_pred
        mock_pred_repo.get_tenant_metadata.side_effect = get_tenant
        mock_pred_repo.get_usage_counter.side_effect = get_counter
        mock_pred_repo.put_capacity_prediction.side_effect = put_pred

        mock_sm.predict_capacity.return_value = {
            "forecast_7_day": 850_000,
            "forecast_30_day": 3_800_000,
            "model_version": "deepar-serverless-v2",
            "anomaly_risk": "LOW",
        }

        yield


def test_health_check_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "HEALTHY"
    # Verify security headers
    assert response.headers["X-Content-Type-Options"] == "nosniff"
    assert response.headers["X-Frame-Options"] == "DENY"
    assert "Strict-Transport-Security" in response.headers


def test_tenant_lifecycle_integration():
    # 1. Onboard new tenant
    create_payload = {
        "name": "Integration Labs LLC",
        "tier": "PRO",
        "contact_email": "admin@integrationlabs.example.com",
        "allowed_countries": ["US", "DE"],
        "monthly_quota": 500_000,
    }
    create_resp = client.post("/v1/tenants", json=create_payload)
    assert create_resp.status_code == 201
    new_tid = create_resp.json()["tenant_id"]
    assert create_resp.json()["name"] == "Integration Labs LLC"

    # 2. Get the new tenant
    get_resp = client.get(f"/v1/tenants/{new_tid}", headers={"CloudFront-Viewer-Country": "US"})
    assert get_resp.status_code == 200
    assert get_resp.json()["tier"] == "PRO"

    # 3. Update the tenant's quota and allowed countries
    update_payload = {
        "monthly_quota": 750_000,
        "allowed_countries": ["US", "DE", "FR"],
    }
    update_resp = client.put(f"/v1/tenants/{new_tid}", json=update_payload, headers={"CloudFront-Viewer-Country": "US"})
    assert update_resp.status_code == 200
    assert update_resp.json()["monthly_quota"] == 750_000
    assert "FR" in update_resp.json()["allowed_countries"]

    # 4. List tenants
    list_resp = client.get("/v1/tenants?limit=10")
    assert list_resp.status_code == 200
    assert list_resp.json()["total"] >= 1

    # 5. Add user to tenant
    user_payload = {
        "email": "developer@integrationlabs.example.com",
        "name": "Jane Dev",
        "role": "OPERATOR",
    }
    user_resp = client.post(f"/v1/tenants/{new_tid}/users", json=user_payload, headers={"CloudFront-Viewer-Country": "US"})
    assert user_resp.status_code == 201
    assert user_resp.json()["role"] == "OPERATOR"

    # 6. List tenant users
    list_users_resp = client.get(f"/v1/tenants/{new_tid}/users", headers={"CloudFront-Viewer-Country": "US"})
    assert list_users_resp.status_code == 200
    assert len(list_users_resp.json()) >= 1


def test_geolocation_enforcement_integration():
    tid = "tenant-alpha-enterprise"

    # Authorized country -> 200 OK
    resp_allowed = client.get(f"/v1/tenants/{tid}", headers={"CloudFront-Viewer-Country": "US"})
    assert resp_allowed.status_code == 200

    # Unauthorized / embargoed country -> 403 Forbidden
    resp_denied = client.get(f"/v1/tenants/{tid}", headers={"CloudFront-Viewer-Country": "KP"})
    assert resp_denied.status_code == 403
    assert resp_denied.json()["error"] == "GEOLOCATION_RESTRICTED"


def test_usage_ingestion_and_metrics_integration():
    tid = "tenant-alpha-enterprise"

    # Single event ingestion
    event_payload = {
        "metric": "api_calls",
        "count": 50,
        "idempotency_key": "test-key-1",
    }
    ingest_resp = client.post(
        "/v1/usage/events",
        json=event_payload,
        headers={"X-Tenant-Id": tid, "CloudFront-Viewer-Country": "US"},
    )
    assert ingest_resp.status_code == 202
    assert ingest_resp.json()["status"] == "QUEUED"

    # Query metrics
    metrics_resp = client.get(
        "/v1/usage?metric=api_calls",
        headers={"X-Tenant-Id": tid, "CloudFront-Viewer-Country": "US"},
    )
    assert metrics_resp.status_code == 200
    assert metrics_resp.json()["metric"] == "api_calls"
    assert metrics_resp.json()["percent_consumed"] >= 0

    # Query history
    history_resp = client.get(
        "/v1/usage/history?metric=api_calls&days=7",
        headers={"X-Tenant-Id": tid, "CloudFront-Viewer-Country": "US"},
    )
    assert history_resp.status_code == 200
    assert history_resp.json()["total_points"] >= 1


def test_billing_and_prediction_endpoints():
    tid = "tenant-alpha-enterprise"

    # Invoice preview
    invoice_resp = client.get(
        "/v1/billing",
        headers={"X-Tenant-Id": tid, "CloudFront-Viewer-Country": "US"},
    )
    assert invoice_resp.status_code == 200
    assert invoice_resp.json()["currency"] == "USD"
    assert invoice_resp.json()["total_due_cents"] > 0

    # Past invoices
    invoices_list = client.get(
        "/v1/billing/invoices",
        headers={"X-Tenant-Id": tid, "CloudFront-Viewer-Country": "US"},
    )
    assert invoices_list.status_code == 200
    assert invoices_list.json()["total"] >= 1

    # Capacity prediction
    prediction_resp = client.get(
        "/v1/predictions/capacity?metric=api_calls",
        headers={"X-Tenant-Id": tid, "CloudFront-Viewer-Country": "US"},
    )
    assert prediction_resp.status_code == 200
    assert prediction_resp.json()["model_version"] == "deepar-serverless-v2"
    assert prediction_resp.json()["forecast_next_7_days"] > 0


# ------------------------------------------------------------------------------
# ERROR SCENARIOS & EDGE CASES
# ------------------------------------------------------------------------------
def test_validation_error_on_invalid_tenant_creation():
    # Missing required email & name too short
    bad_payload = {
        "name": "A",
        "monthly_quota": -500,  # Negative quota
    }
    response = client.post("/v1/tenants", json=bad_payload)
    assert response.status_code == 400
    data = response.json()
    assert data["error"] == "VALIDATION_ERROR"


def test_validation_error_on_invalid_country_code():
    bad_payload = {
        "name": "Invalid Country Corp",
        "tier": "STARTER",
        "contact_email": "corp@example.com",
        "allowed_countries": ["USA", "12"],  # Invalid ISO codes
        "monthly_quota": 50000,
    }
    response = client.post("/v1/tenants", json=bad_payload)
    assert response.status_code == 400
    assert "VALIDATION_ERROR" in response.json()["error"]


def test_not_found_on_unknown_tenant():
    response = client.get("/v1/tenants/non-existent-tenant-9999", headers={"CloudFront-Viewer-Country": "US"})
    assert response.status_code == 404
    assert response.json()["error"] == "TENANT_NOT_FOUND"


def test_user_creation_invalid_role():
    bad_user_payload = {
        "email": "user@example.com",
        "name": "Bad Role User",
        "role": "SUPERUSER_INVALID",
    }
    response = client.post(
        "/v1/tenants/tenant-alpha-enterprise/users",
        json=bad_user_payload,
        headers={"CloudFront-Viewer-Country": "US"},
    )
    assert response.status_code == 400
    assert response.json()["error"] == "VALIDATION_ERROR"
