"""API tests: scan lifecycle endpoints (orchestrator + scope mocked)."""

from __future__ import annotations

from app.models.scan import Scan, ScanProfile, ScanStatus
from app.schemas.scan import ScanProgress

from tests.conftest import auth_headers, make_user


class FakeOrchestrator:
    def __init__(self):
        self.dispatched: list[int] = []
        self.cancelled: list[int] = []

    async def dispatch(self, scan_id: int) -> None:
        self.dispatched.append(scan_id)

    async def cancel(self, scan_id: int) -> None:
        self.cancelled.append(scan_id)

    async def progress(self, scan_id: int, session) -> ScanProgress:
        scan = await session.get(Scan, scan_id)
        if scan and scan.status == ScanStatus.COMPLETED:
            return ScanProgress(scan_id=scan_id, phase="done", percent=100)
        return ScanProgress(scan_id=scan_id, phase="queued", percent=0)


async def _noop_scope(target_url, session):
    return None


async def make_scan(session_factory, **kw) -> Scan:
    defaults = dict(
        target_url="https://app.example.com/",
        profile=ScanProfile.STANDARD,
        status=ScanStatus.PENDING,
    )
    defaults.update(kw)
    async with session_factory() as session:
        scan = Scan(**defaults)
        session.add(scan)
        await session.commit()
        await session.refresh(scan)
        return scan


async def test_create_scan_dispatches_and_records_requester(client, session_factory, monkeypatch):
    user = await make_user(session_factory, "op@test.dev", "scanner")
    fake = FakeOrchestrator()
    monkeypatch.setattr("app.api.v1.scans.orchestrator", fake)
    monkeypatch.setattr("app.api.v1.scans.assert_target_allowed", _noop_scope)

    resp = await client.post(
        "/api/v1/scans",
        headers=auth_headers(user),
        json={"target_url": "https://app.example.com/", "profile": "deep"},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["status"] == "pending" and body["profile"] == "deep"
    assert fake.dispatched == [body["id"]]

    async with session_factory() as session:
        stored = await session.get(Scan, body["id"])
        assert stored.requested_by == user.id


async def test_create_scan_rbac(client, session_factory, monkeypatch):
    monkeypatch.setattr("app.api.v1.scans.orchestrator", FakeOrchestrator())
    monkeypatch.setattr("app.api.v1.scans.assert_target_allowed", _noop_scope)
    viewer = await make_user(session_factory, "v@test.dev", "viewer")

    no_auth = await client.post("/api/v1/scans", json={"target_url": "https://a.example.com/"})
    assert no_auth.status_code == 401

    forbidden = await client.post(
        "/api/v1/scans", headers=auth_headers(viewer), json={"target_url": "https://a.example.com/"}
    )
    assert forbidden.status_code == 403


async def test_create_scan_url_validation(client, session_factory, monkeypatch):
    user = await make_user(session_factory, "op@test.dev", "scanner")
    monkeypatch.setattr("app.api.v1.scans.orchestrator", FakeOrchestrator())
    monkeypatch.setattr("app.api.v1.scans.assert_target_allowed", _noop_scope)
    headers = auth_headers(user)

    for bad in ("ftp://x", "not-a-url", "https://user:pass@a.example.com/"):
        resp = await client.post("/api/v1/scans", headers=headers, json={"target_url": bad})
        assert resp.status_code == 422, bad


async def test_scope_gate_rejects_target(client, session_factory, monkeypatch):
    from app.core.errors import TargetNotAllowed

    async def _deny(target_url, session):
        raise TargetNotAllowed("out of scope")

    user = await make_user(session_factory, "op@test.dev", "scanner")
    fake = FakeOrchestrator()
    monkeypatch.setattr("app.api.v1.scans.orchestrator", fake)
    monkeypatch.setattr("app.api.v1.scans.assert_target_allowed", _deny)

    resp = await client.post(
        "/api/v1/scans",
        headers=auth_headers(user),
        json={"target_url": "https://evil.example.com/"},
    )
    assert resp.status_code == 403
    assert resp.json()["error"]["code"] == "target_not_allowed"
    assert fake.dispatched == []


async def test_list_filter_and_pagination(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    await make_scan(
        session_factory, target_url="https://a.example.com/", status=ScanStatus.COMPLETED
    )
    await make_scan(session_factory, target_url="https://b.example.com/")
    headers = auth_headers(user)

    all_scans = await client.get("/api/v1/scans", headers=headers)
    assert len(all_scans.json()) == 2

    completed = await client.get("/api/v1/scans?status=completed", headers=headers)
    assert len(completed.json()) == 1
    assert completed.json()[0]["target_url"] == "https://a.example.com/"

    page = await client.get("/api/v1/scans?page=1&page_size=1", headers=headers)
    assert len(page.json()) == 1


async def test_get_progress_and_counts(client, session_factory):
    user = await make_user(session_factory, "v@test.dev", "viewer")
    scan = await make_scan(session_factory)
    headers = auth_headers(user)

    detail = await client.get(f"/api/v1/scans/{scan.id}", headers=headers)
    assert detail.status_code == 200

    progress = await client.get(f"/api/v1/scans/{scan.id}/progress", headers=headers)
    assert progress.status_code == 200
    assert progress.json()["phase"] == "queued"

    counts = await client.get(f"/api/v1/scans/{scan.id}/findings/count", headers=headers)
    assert counts.json() == {}

    missing = await client.get("/api/v1/scans/424242", headers=headers)
    assert missing.status_code == 404

    missing_counts = await client.get("/api/v1/scans/424242/findings/count", headers=headers)
    assert missing_counts.status_code == 404


async def test_cancel_running_scan(client, session_factory, monkeypatch):
    user = await make_user(session_factory, "op@test.dev", "scanner")
    scan = await make_scan(session_factory, status=ScanStatus.RUNNING)
    fake = FakeOrchestrator()
    monkeypatch.setattr("app.api.v1.scans.orchestrator", fake)

    resp = await client.post(f"/api/v1/scans/{scan.id}/cancel", headers=auth_headers(user))
    assert resp.status_code == 200
    assert fake.cancelled == [scan.id]

    # mark terminal (as the real orchestrator would) → further cancels 409
    async with session_factory() as session:
        stored = await session.get(Scan, scan.id)
        stored.status = ScanStatus.CANCELLED
        await session.commit()
    again = await client.post(f"/api/v1/scans/{scan.id}/cancel", headers=auth_headers(user))
    assert again.status_code == 409


async def test_cancel_terminal_scan_409(client, session_factory):
    user = await make_user(session_factory, "op@test.dev", "scanner")
    scan = await make_scan(session_factory, status=ScanStatus.COMPLETED)
    resp = await client.post(f"/api/v1/scans/{scan.id}/cancel", headers=auth_headers(user))
    assert resp.status_code == 409
