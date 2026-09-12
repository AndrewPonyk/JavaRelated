VALID = {
    "name": "orders-daily",
    "schedule": "0 2 * * *",
    "source": "s3://lake/raw/orders/",
    "target": "analytics.marts.fct_business_metrics_daily",
}


def test_create_and_get_roundtrip(client):
    created = client.post("/api/v1/pipelines", json=VALID)
    assert created.status_code == 201
    body = created.json()
    assert body["status"] == "draft"

    fetched = client.get(f"/api/v1/pipelines/{body['id']}")
    assert fetched.status_code == 200
    assert fetched.json()["name"] == "orders-daily"


def test_list_contains_created(client):
    client.post("/api/v1/pipelines", json=VALID)
    response = client.get("/api/v1/pipelines")
    assert response.status_code == 200
    assert len(response.json()) == 1


def test_validation_rejects_bad_name(client):
    response = client.post("/api/v1/pipelines", json={**VALID, "name": "X"})
    assert response.status_code == 422


def test_validation_rejects_bad_cron(client):
    response = client.post("/api/v1/pipelines", json={**VALID, "schedule": "hourly"})
    assert response.status_code == 422


def test_duplicate_name_conflicts(client):
    assert client.post("/api/v1/pipelines", json=VALID).status_code == 201
    assert client.post("/api/v1/pipelines", json=VALID).status_code == 409


def test_partial_update(client):
    pipeline_id = client.post("/api/v1/pipelines", json=VALID).json()["id"]

    patched = client.patch(
        f"/api/v1/pipelines/{pipeline_id}",
        json={"description": "governed daily orders load", "status": "active"},
    )

    assert patched.status_code == 200
    body = patched.json()
    assert body["description"] == "governed daily orders load"
    assert body["status"] == "active"
    assert body["name"] == "orders-daily"  # untouched fields preserved


def test_missing_pipeline_returns_404(client):
    assert client.get("/api/v1/pipelines/doesnotexist").status_code == 404


def test_delete_then_gone(client):
    pipeline_id = client.post("/api/v1/pipelines", json=VALID).json()["id"]
    assert client.delete(f"/api/v1/pipelines/{pipeline_id}").status_code == 204
    assert client.get(f"/api/v1/pipelines/{pipeline_id}").status_code == 404


def test_cron_semantics_validated(client):
    ok = client.post("/api/v1/pipelines", json={**VALID, "schedule": "*/15 0-6 * * mon"})
    assert ok.status_code == 201

    for bad in ("* * * *", "0 2 * * * *", "every-morning x y z w", "0 2 * * $"):
        response = client.post(
            "/api/v1/pipelines", json={**VALID, "name": "cron-check", "schedule": bad}
        )
        assert response.status_code == 422, f"{bad!r} should be rejected"
