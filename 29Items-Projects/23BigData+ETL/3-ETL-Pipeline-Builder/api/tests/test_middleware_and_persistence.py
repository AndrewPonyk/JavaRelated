import asyncio

from app.services.pipeline_service import PipelineService, RedisPipelineRepository

VALID = {
    "name": "orders-daily",
    "schedule": "0 2 * * *",
    "source": "s3://lake/raw/orders/",
    "target": "analytics.marts.fct_business_metrics_daily",
}


def test_request_id_is_issued_and_echoed(client):
    fresh = client.get("/healthz")
    assert fresh.headers.get("x-request-id")

    supplied = client.get("/healthz", headers={"X-Request-ID": "trace-me-123"})
    assert supplied.headers["x-request-id"] == "trace-me-123"


def test_pipelines_survive_service_restart(client, fake_redis):
    created = client.post("/api/v1/pipelines", json=VALID)
    assert created.status_code == 201

    # Simulate an API restart: brand-new service over the same store.
    reborn = PipelineService(RedisPipelineRepository(client=fake_redis))
    items = asyncio.run(reborn.list())

    assert [p.name for p in items] == ["orders-daily"]


def test_rename_to_existing_name_conflicts(client):
    first = client.post("/api/v1/pipelines", json=VALID).json()
    second = client.post("/api/v1/pipelines", json={**VALID, "name": "orders-hourly"}).json()

    conflict = client.patch(f"/api/v1/pipelines/{second['id']}", json={"name": "orders-daily"})
    assert conflict.status_code == 409

    renamed = client.patch(f"/api/v1/pipelines/{first['id']}", json={"name": "orders-nightly"})
    assert renamed.status_code == 200

    # Old name is released for reuse after the rename.
    reuse = client.post("/api/v1/pipelines", json=VALID)
    assert reuse.status_code == 201
