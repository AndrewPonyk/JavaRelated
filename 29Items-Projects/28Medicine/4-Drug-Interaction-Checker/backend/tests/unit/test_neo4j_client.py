"""Unit tests for the Neo4j driver lifecycle."""

import pytest

import app.db.neo4j_client as nc


class _FakeDriver:
    def __init__(self) -> None:
        self.closed = False
        self.fail = False

    async def verify_connectivity(self):
        if self.fail:
            raise RuntimeError("no connection")

    async def close(self):
        self.closed = True


async def test_init_get_close(monkeypatch):
    nc._driver = None
    fake = _FakeDriver()
    monkeypatch.setattr(nc.AsyncGraphDatabase, "driver", staticmethod(lambda *a, **k: fake))

    driver = await nc.init_driver()
    assert driver is fake
    assert nc.get_driver() is fake
    # init_driver is idempotent
    assert await nc.init_driver() is fake

    assert await nc.verify_connectivity() is True

    await nc.close_driver()
    assert fake.closed is True
    with pytest.raises(RuntimeError):
        nc.get_driver()


async def test_verify_connectivity_failure(monkeypatch):
    fake = _FakeDriver()
    fake.fail = True
    monkeypatch.setattr(nc, "_driver", fake)
    assert await nc.verify_connectivity() is False
    nc._driver = None
