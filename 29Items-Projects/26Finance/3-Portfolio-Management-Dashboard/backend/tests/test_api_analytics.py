from __future__ import annotations


def test_attribution(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.get(
        f"/api/v1/portfolios/{pid}/attribution?lookback_days=252", headers=auth_headers
    )
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["assets"]) == 3
    # Risk contributions sum to the portfolio volatility (Euler decomposition).
    risk_sum = sum(a["risk_contribution"] for a in body["assets"])
    assert abs(risk_sum - body["portfolio_volatility"]) < 1e-6


def test_backtest(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.post(
        f"/api/v1/portfolios/{pid}/backtest",
        json={
            "lookback": 60,
            "rebalance_every": 21,
            "objective": "max_sharpe",
            "lookback_days": 504,
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["strategy_equity"]) == len(body["dates"])
    assert len(body["benchmark_equity"]) == len(body["dates"])
    assert body["n_rebalances"] >= 1


def test_report_csv(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.get(f"/api/v1/portfolios/{pid}/report.csv", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/csv")
    text = resp.text
    assert "Metric,Value" in text
    assert "Symbol,Weight" in text
