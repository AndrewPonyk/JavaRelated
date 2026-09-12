"""Lessons CRUD API tests + seed behaviour + audit metadata policy."""

import uuid

from crypto_toolkit.extensions import db
from crypto_toolkit.models import Lesson
from crypto_toolkit.services.seed import seed_if_empty


def _slug():
    return f"lesson-{uuid.uuid4().hex[:8]}"


def _create(client, headers, **overrides):
    payload = {
        "slug": _slug(),
        "title": "Temporal lesson",
        "topic": "aes",
        "content_md": "# hi",
        "order_index": 99,
    }
    payload.update(overrides)
    return client.post("/api/lessons/", json=payload, headers=headers)


class TestCrud:
    def test_create_read_update_delete(self, client, admin_headers):
        slug = _slug()
        res = client.post(
            "/api/lessons/",
            json={
                "slug": slug,
                "title": "Lesson",
                "topic": "rsa",
                "content_md": "body",
            },
            headers=admin_headers,
        )
        assert res.status_code == 201
        created = res.get_json()["data"]
        assert created["slug"] == slug and created["topic"] == "rsa"

        got = client.get(f"/api/lessons/{slug}").get_json()["data"]
        assert got["title"] == "Lesson"

        res = client.patch(
            f"/api/lessons/{slug}", json={"title": "Renamed", "order_index": 5}, headers=admin_headers
        )
        assert res.get_json()["data"]["title"] == "Renamed"
        assert res.get_json()["data"]["order_index"] == 5

        res = client.delete(f"/api/lessons/{slug}", headers=admin_headers)
        assert res.get_json()["data"]["deleted"] == slug
        assert client.get(f"/api/lessons/{slug}").status_code == 404

    def test_duplicate_slug_conflict(self, client, admin_headers):
        first = _create(client, admin_headers)
        slug = first.get_json()["data"]["slug"]
        res = client.post(
            "/api/lessons/",
            json={
                "slug": slug,
                "title": "Dup",
                "topic": "aes",
            },
            headers=admin_headers,
        )
        assert res.status_code == 409

    def test_missing_lesson_404(self, client):
        res = client.get("/api/lessons/does-not-exist")
        assert res.status_code == 404
        assert res.get_json()["error"]["code"] == "not_found"

    def test_invalid_topic_422(self, client, admin_headers):
        res = _create(client, admin_headers, topic="des")
        assert res.status_code == 422

    def test_invalid_slug_format_422(self, client, admin_headers):
        res = _create(client, admin_headers, slug="Bad Slug!")
        assert res.status_code == 422

    def test_list_filtering_and_ordering(self, client, admin_headers):
        _create(client, admin_headers, topic="sha3", order_index=10)
        _create(client, admin_headers, topic="sha3", order_index=5)
        data = client.get("/api/lessons/?topic=sha3").get_json()["data"]
        assert data["total"] >= 2
        orders = [item["order_index"] for item in data["items"]]
        assert orders == sorted(orders)

    def test_unknown_topic_filter_422(self, client):
        assert client.get("/api/lessons/?topic=md5").status_code == 422

    def test_pagination_window_and_clamping(self, client, admin_headers):
        for order in (81, 82, 83):
            _create(client, admin_headers, topic="aes", order_index=order)
        data = client.get("/api/lessons/?topic=aes&limit=2").get_json()["data"]
        assert data["total"] >= 3
        assert len(data["items"]) == 2

        # offset past a known total returns an empty page, not an error
        data = client.get(f"/api/lessons/?topic=aes&limit=2&offset={data['total']}").get_json()["data"]
        assert data["items"] == []

        # limit is clamped to [1, 100]: 0 does not mean "all"
        assert len(client.get("/api/lessons/?limit=0").get_json()["data"]["items"]) == 1


class TestSeeding:
    def test_seed_if_empty_idempotent(self, app):
        with app.app_context():
            Lesson.query.delete()  # isolate from CRUD tests sharing the session DB
            db.session.commit()
            assert seed_if_empty() == 6
            assert seed_if_empty() == 0  # second run: no-op
            assert Lesson.query.count() == 6
            slugs = {lesson.slug for lesson in Lesson.query}
            assert "tls13-handshake-walkthrough" in slugs

    def test_seeded_lesson_lists_anonymously(self, client):
        res = client.get("/api/lessons/")
        assert res.status_code == 200  # no auth needed
        data = res.get_json()["data"]
        assert data["total"] >= 6
        topics = {lesson["topic"] for lesson in data["items"]}
        assert topics == {"aes", "rsa", "ecdsa", "sha3", "argon2", "tls"}


class TestAuditPolicy:
    def test_audit_rows_contain_no_secret_params(self, client, admin_headers):
        import base64
        import os

        key = base64.b64encode(os.urandom(32)).decode()
        client.post("/api/aes/encrypt", json={"plaintext": "hidden message", "key_b64": key})
        rows = client.get("/api/auth/audit", headers=admin_headers).get_json()["data"]["items"]
        aes_rows = [row for row in rows if row["op"] == "aes.encrypt"]
        assert aes_rows, "audit row for aes.encrypt missing"
        for row in aes_rows:
            flat = str(row["params"])
            assert "hidden message" not in flat and key not in flat
