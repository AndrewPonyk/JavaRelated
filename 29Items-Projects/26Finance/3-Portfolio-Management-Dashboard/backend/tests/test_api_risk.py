from __future__ import annotations


def test_risk_metrics(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.get(f"/api/v1/portfolios/{pid}/risk?lookback_days=252", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    for key in ("sharpe_ratio", "var_historical", "cvar_historical", "max_drawdown"):
        assert key in body
    assert body["cvar_historical"] >= body["var_historical"]


def test_monte_carlo_job_flow(client, auth_headers, portfolio):
    pid = portfolio["id"]
    dispatched = client.post(
        f"/api/v1/portfolios/{pid}/monte-carlo",
        json={"n_days": 60, "n_sims": 2000, "seed": 1},
        headers=auth_headers,
    )
    assert dispatched.status_code == 202
    job_id = dispatched.json()["job_id"]

    job = client.get(f"/api/v1/jobs/{job_id}", headers=auth_headers)
    assert job.status_code == 200
    body = job.json()
    # Eager mode -> job completes inline.
    assert body["status"] == "done"
    result = body["result"]
    assert result["var"] >= 0
    assert result["cvar"] >= result["var"]
    assert len(result["bands"]["p50"]) == 60 + 1


def test_job_ownership_and_missing(client, auth_headers, other_headers, portfolio):
    pid = portfolio["id"]
    job_id = client.post(
        f"/api/v1/portfolios/{pid}/monte-carlo",
        json={"n_days": 30, "n_sims": 1000, "seed": 2},
        headers=auth_headers,
    ).json()["job_id"]

    # Another user cannot read this job.
    assert client.get(f"/api/v1/jobs/{job_id}", headers=other_headers).status_code == 404
    # Unknown job id.
    assert client.get("/api/v1/jobs/deadbeef", headers=auth_headers).status_code == 404
