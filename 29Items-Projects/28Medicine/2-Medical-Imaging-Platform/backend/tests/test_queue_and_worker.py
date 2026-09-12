"""Queue abstraction + ingestion worker dispatch tests."""

from __future__ import annotations

from app.core.config import settings
from app.services.queue_service import InProcessQueue, get_queue_client
from app.services.study_service import StudyService

from tests.conftest import build_dicom


def test_inprocess_queue_fifo():
    q = InProcessQueue()
    q.send({"n": 1})
    q.send({"n": 2})
    got = q.receive(max_messages=10)
    assert [m["n"] for m in got] == [1, 2]
    assert q.receive() == []


def test_inprocess_queue_respects_max():
    q = InProcessQueue()
    for i in range(5):
        q.send({"n": i})
    assert len(q.receive(max_messages=2)) == 2


def test_get_queue_client_defaults_to_inproc():
    assert isinstance(get_queue_client(), InProcessQueue)


async def test_worker_processes_ingest_message(session_factory, storage, monkeypatch):
    import app.workers.ingestion_worker as worker

    monkeypatch.setattr(worker, "SessionFactory", session_factory)
    monkeypatch.setattr(worker, "get_storage_service", lambda: storage)

    raw = build_dicom("DX", StudyInstanceUID="WK1")
    storage.put_object(settings.s3_bucket_staging, "staging/x.dcm", raw, "application/dicom")

    await worker.process_message({"type": "ingest", "object_key": "staging/x.dcm"})

    async with session_factory() as s:
        study, count = await StudyService(s).get_by_uid("WK1")
        assert study.study_instance_uid == "WK1"
        assert count == 1


async def test_worker_ignores_unknown_message(session_factory, storage, monkeypatch):
    import app.workers.ingestion_worker as worker

    monkeypatch.setattr(worker, "SessionFactory", session_factory)
    monkeypatch.setattr(worker, "get_storage_service", lambda: storage)
    # should not raise
    await worker.process_message({"type": "bogus"})
