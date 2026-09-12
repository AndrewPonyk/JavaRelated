"""API tests: finding queries — filtering, pagination, stats."""

from __future__ import annotations

from app.models.finding import Finding, Severity, Source
from app.models.scan import Scan

from tests.conftest import auth_headers, make_user


async def seed_findings(session_factory, n: int = 5) -> Scan:
    async with session_factory() as session:
        scan = Scan(target_url="https://app.example.com/")
        session.add(scan)
        await session.flush()
        severities = [
            Severity.CRITICAL,
            Severity.HIGH,
            Severity.MEDIUM,
            Severity.LOW,
            Severity.INFO,
        ]
        for i in range(n):
            session.add(
                Finding(
                    scan_id=scan.id,
                    source=Source.ZAP if i % 2 else Source.SQLMAP,
                    rule_id=f"rule-{i}",
                    title=f"Finding {i}",
                    url=f"https://app.example.com/page{i}",
                    param=f"p{i}",
                    severity=severities[i % len(severities)],
                    owasp_category="A03:2021" if i < 2 else None,
                    cwe_id="CWE-89",
                    dedup_hash=f"hash-{i}",
                    evidence={"excerpt": "x"},
                    raw={},
                )
            )
        await session.commit()
        await session.refresh(scan)
        return scan


async def test_list_and_filter(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    scan = await seed_findings(session_factory)
    headers = auth_headers(user)

    page = await client.get("/api/v1/findings", headers=headers)
    body = page.json()
    assert body["total"] == 5 and len(body["items"]) == 5
    assert body["page"] == 1

    only_high = await client.get("/api/v1/findings?severity=high", headers=headers)
    assert only_high.json()["total"] == 1
    assert only_high.json()["items"][0]["severity"] == "high"

    for_scan = await client.get(f"/api/v1/findings?scan_id={scan.id}", headers=headers)
    assert for_scan.json()["total"] == 5

    owasp = await client.get("/api/v1/findings?owasp_category=A03:2021", headers=headers)
    assert owasp.json()["total"] == 2


async def test_pagination_metadata(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    await seed_findings(session_factory, n=5)
    page = await client.get("/api/v1/findings?page=2&page_size=2", headers=auth_headers(user))
    body = page.json()
    assert body["page"] == 2 and body["page_size"] == 2
    assert body["total"] == 5 and len(body["items"]) == 2


async def test_bad_owasp_pattern_422(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    resp = await client.get(
        "/api/v1/findings?owasp_category=not-a-category", headers=auth_headers(user)
    )
    assert resp.status_code == 422


async def test_detail_and_404(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    await seed_findings(session_factory, n=1)
    headers = auth_headers(user)
    finding = (await client.get("/api/v1/findings", headers=headers)).json()["items"][0]
    detail = await client.get(f"/api/v1/findings/{finding['id']}", headers=headers)
    assert detail.status_code == 200
    assert detail.json()["title"] == finding["title"]
    assert (await client.get("/api/v1/findings/999", headers=headers)).status_code == 404


async def test_severity_stats(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    await seed_findings(session_factory, n=5)
    resp = await client.get("/api/v1/findings/stats/severity", headers=auth_headers(user))
    stats = {row["severity"]: row["count"] for row in resp.json()}
    assert stats == {"critical": 1, "high": 1, "medium": 1, "low": 1, "info": 1}
