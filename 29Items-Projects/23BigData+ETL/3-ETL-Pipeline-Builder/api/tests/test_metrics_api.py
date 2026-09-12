def test_healthz(client):
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_list_current_metrics(client):
    response = client.get("/api/v1/metrics/current")
    assert response.status_code == 200
    body = response.json()
    assert body[0]["metric"] == "orders_per_second"
    assert body[0]["value"] == 42.0


def test_get_single_metric(client):
    response = client.get("/api/v1/metrics/orders_per_second/current")
    assert response.status_code == 200
    assert response.json()["window_ms"] == 500


def test_unknown_metric_returns_404(client):
    response = client.get("/api/v1/metrics/nope/current")
    assert response.status_code == 404


def test_history_validates_limit(client):
    response = client.get("/api/v1/metrics/orders_per_second/history?limit=999999")
    assert response.status_code == 422


def test_history_empty_until_warehouse_wired(client):
    response = client.get("/api/v1/metrics/orders_per_second/history")
    assert response.status_code == 200
    assert response.json() == []


def test_websocket_pushes_hot_store_updates(client):
    with client.websocket_connect("/api/v1/metrics/stream") as websocket:
        message = websocket.receive_json()
    assert message["metric"] == "orders_per_second"
    assert message["value"] == 43.5
