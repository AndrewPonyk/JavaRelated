"""Local artifact store: round-trip, payload encoding, traversal defense."""

import pytest

from app.services.artifact_store import ArtifactRef, LocalArtifactStore


def test_save_read_roundtrip(tmp_path):
    store = LocalArtifactStore(str(tmp_path))
    ref = store.save("comp-123", "plot.svg", b"<svg/>", "image/svg+xml")
    assert ref.storage == "local"
    assert ref.key == "comp-123/plot.svg"
    assert store.read(ref) == b"<svg/>"


def test_payload_roundtrip(tmp_path):
    store = LocalArtifactStore(str(tmp_path))
    ref = store.save("comp-1", "plot.svg", b"data", "image/svg+xml")
    restored = ArtifactRef.from_payload(ref.to_payload())
    assert restored == ref


def test_path_traversal_rejected(tmp_path):
    store = LocalArtifactStore(str(tmp_path / "base"))
    hostile = ArtifactRef(storage="local", key="../../etc/passwd", content_type="text/plain")
    with pytest.raises(ValueError, match="escapes"):
        store.read(hostile)
