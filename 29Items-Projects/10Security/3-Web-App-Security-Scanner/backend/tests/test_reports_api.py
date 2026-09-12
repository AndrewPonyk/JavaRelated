"""API tests: SARIF + HTML report exports (incl. XSS autoescaping)."""

from __future__ import annotations

from app.models.finding import Finding, Severity, Source
from app.models.scan import Scan

from tests.conftest import auth_headers, make_user


async def seed(session_factory, title: str) -> Scan:
    async with session_factory() as session:
        scan = Scan(target_url="https://app.example.com/")
        session.add(scan)
        await session.flush()
        session.add(
            Finding(
                scan_id=scan.id,
                source=Source.XSS_ENGINE,
                rule_id="XSS-HTML_BODY",
                title=title,
                url="https://app.example.com/?q=marked",
                param="q",
                severity=Severity.HIGH,
                severity_confidence=0.87,
                owasp_category="A03:2021",
                cwe_id="CWE-79",
                dedup_hash="h1",
                evidence={"payload": "<script>zzz</script>"},
                raw={},
            )
        )
        await session.commit()
        await session.refresh(scan)
        return scan


async def test_sarif_export(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    scan = await seed(session_factory, "Reflected XSS")
    resp = await client.get(f"/api/v1/reports/{scan.id}/sarif", headers=auth_headers(user))
    assert resp.status_code == 200
    sarif = resp.json()
    assert sarif["version"] == "2.1.0"
    result = sarif["runs"][0]["results"][0]
    assert result["ruleId"] == "XSS-HTML_BODY"
    assert result["level"] == "high"
    assert result["properties"]["cwe"] == "CWE-79"


async def test_html_report_escapes_attacker_content(client, session_factory):
    """Finding titles/evidence are attacker-controlled — must be autoescaped."""
    user = await make_user(session_factory, "v@test.dev", "viewer")
    evil_title = "<script>alert(1)</script>"
    scan = await seed(session_factory, evil_title)
    resp = await client.get(f"/api/v1/reports/{scan.id}/html", headers=auth_headers(user))
    assert resp.status_code == 200
    html = resp.text
    assert "text/html" in resp.headers["content-type"]
    assert "&lt;script&gt;alert(1)&lt;/script&gt;" in html  # escaped title
    assert "<script>alert(1)</script>" not in html  # no raw injection
    assert "app.example.com" in html
    assert "severity model" in html  # classifier footer


async def test_reports_404(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    headers = auth_headers(user)
    assert (await client.get("/api/v1/reports/999/sarif", headers=headers)).status_code == 404
    assert (await client.get("/api/v1/reports/999/html", headers=headers)).status_code == 404
