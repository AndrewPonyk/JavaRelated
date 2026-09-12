"""Interactive plot rendering and ML classification endpoints."""


def test_plot_function_returns_svg(client):
    response = client.post(
        "/api/v1/plots/function",
        json={"expression": "sin(x)/x", "variable": "x", "x_min": -15, "x_max": 15},
    )
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("image/svg+xml")
    assert b"<svg" in response.content
    assert "Cache-Control" in response.headers


def test_plot_rejects_inverted_range(client):
    response = client.post(
        "/api/v1/plots/function",
        json={"expression": "x", "x_min": 5, "x_max": -5},
    )
    assert response.status_code == 422


def test_plot_of_unparseable_expression_is_422(client):
    response = client.post("/api/v1/plots/function", json={"expression": "open(x) @@"})
    assert response.status_code == 422


def test_ml_classify_heuristic_path(client):
    response = client.post("/api/v1/ml/classify", json={"expression": "3x^2 + 2x - 1"})
    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "quadratic"
    assert body["source"] == "heuristic"
    assert 0.0 <= body["confidence"] <= 1.0


def test_ml_classify_parse_error(client):
    response = client.post("/api/v1/ml/classify", json={"expression": "]] nope [["})
    assert response.status_code == 422
