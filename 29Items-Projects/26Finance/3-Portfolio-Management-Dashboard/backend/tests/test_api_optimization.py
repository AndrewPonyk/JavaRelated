from __future__ import annotations


def test_optimize_max_sharpe(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.post(
        f"/api/v1/portfolios/{pid}/optimize",
        json={"objective": "max_sharpe"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["objective"] == "max_sharpe"
    assert abs(sum(body["weights"].values()) - 1.0) < 1e-4
    assert "sharpe" in body and "volatility" in body


def test_optimize_min_variance(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.post(
        f"/api/v1/portfolios/{pid}/optimize",
        json={"objective": "min_variance"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["objective"] == "min_variance"


def test_optimize_respects_max_weight(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.post(
        f"/api/v1/portfolios/{pid}/optimize",
        json={"objective": "max_sharpe", "max_weight": 0.5},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert all(w <= 0.5 + 1e-4 for w in resp.json()["weights"].values())


def test_infeasible_max_weight_is_domain_error(client, auth_headers, portfolio):
    pid = portfolio["id"]
    # 3 assets, max 0.2 each -> can't sum to 1.0 -> infeasible.
    resp = client.post(
        f"/api/v1/portfolios/{pid}/optimize",
        json={"objective": "max_sharpe", "max_weight": 0.2},
        headers=auth_headers,
    )
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "domain_error"


def test_efficient_frontier(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.post(
        f"/api/v1/portfolios/{pid}/frontier",
        json={"n_points": 20},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["points"]) >= 2
    assert "max_sharpe" in body and "min_variance" in body
    # Frontier returns should be sorted ascending.
    returns = [p["expected_return"] for p in body["points"]]
    assert returns == sorted(returns)
