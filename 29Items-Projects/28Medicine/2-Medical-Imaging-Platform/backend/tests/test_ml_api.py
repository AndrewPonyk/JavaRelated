"""ML API: trigger routing + results retrieval."""

from __future__ import annotations

from app.services.ingestion_service import IngestionService
from app.services.instance_service import InstanceService
from app.services.ml_service import MlService

from tests.conftest import build_dicom

PREFIX = "/api/v1"
DICOM_CT = {"content-type": "application/dicom"}


async def _stow(client, headers, raw):
    return await client.post(
        f"{PREFIX}/dicomweb/studies", content=raw, headers={**headers, **DICOM_CT}
    )


async def _sop_for(client, headers, study_uid, modality="DX") -> str:
    await _stow(client, headers, build_dicom(modality, StudyInstanceUID=study_uid))
    listing = await client.get(f"{PREFIX}/studies/{study_uid}/instances", headers=headers)
    return listing.json()[0]["sop_instance_uid"]


async def test_trigger_chest_xray_queues(client, rad_headers):
    sop = await _sop_for(client, rad_headers, "MLQ", "DX")
    resp = await client.post(
        f"{PREFIX}/ml/trigger", headers=rad_headers, json={"sop_instance_uid": sop}
    )
    assert resp.status_code == 200
    assert resp.json()["status"] == "queued"


async def test_trigger_unsupported_modality(client, rad_headers):
    sop = await _sop_for(client, rad_headers, "MLCT", "CT")
    resp = await client.post(
        f"{PREFIX}/ml/trigger", headers=rad_headers, json={"sop_instance_uid": sop}
    )
    assert resp.status_code == 200
    assert resp.json()["status"] == "skipped_unsupported_modality"


async def test_trigger_unknown_instance_404(client, rad_headers):
    resp = await client.post(
        f"{PREFIX}/ml/trigger", headers=rad_headers, json={"sop_instance_uid": "ghost"}
    )
    assert resp.status_code == 404


async def test_results_returns_stored_predictions(client, session_factory, storage, rad_headers):
    # Seed a study + a stored ML result via a closed session, then query the API.
    async with session_factory() as s:
        res = await IngestionService(s, storage).ingest(build_dicom("DX", StudyInstanceUID="MLR"))
        instance = await InstanceService(s).get_by_sop_uid(res.sop_instance_uid)
        await MlService(s).store_result(
            instance_pk=instance.id,
            model_name="chexnet",
            model_version="v1",
            predictions={"Pneumonia": 0.91, "Cardiomegaly": 0.05},
            top_label="Pneumonia",
            top_score=0.91,
        )
        await s.commit()

    resp = await client.get(f"{PREFIX}/ml/results/MLR", headers=rad_headers)
    assert resp.status_code == 200
    results = resp.json()
    assert len(results) == 1
    assert results[0]["top_label"] == "Pneumonia"
    assert results[0]["predictions"]["Pneumonia"] == 0.91


async def test_results_empty_for_unknown_study(client, rad_headers):
    resp = await client.get(f"{PREFIX}/ml/results/none", headers=rad_headers)
    assert resp.status_code == 200
    assert resp.json() == []


async def test_trigger_inline_completed_then_exists(client, rad_headers, monkeypatch):
    """Inline mode runs inference (mocked) and the second call short-circuits."""
    from app.core.config import settings
    from app.services.ml_service import MlService

    monkeypatch.setattr(settings, "ml_inline", True)

    async def fake_infer(self, instance):
        return await self.store_result(
            instance_pk=instance.id,
            model_name="chexnet",
            model_version="test",
            predictions={"Pneumonia": 0.7},
            top_label="Pneumonia",
            top_score=0.7,
        )

    monkeypatch.setattr(MlService, "infer_and_store", fake_infer)

    sop = await _sop_for(client, rad_headers, "MLINLINE", "DX")
    first = await client.post(
        f"{PREFIX}/ml/trigger", headers=rad_headers, json={"sop_instance_uid": sop}
    )
    assert first.json()["status"] == "completed"

    second = await client.post(
        f"{PREFIX}/ml/trigger", headers=rad_headers, json={"sop_instance_uid": sop}
    )
    assert second.json()["status"] == "exists"


async def test_results_include_heatmap_url(client, session_factory, storage, rad_headers):
    from app.core.config import settings
    from app.services.ingestion_service import IngestionService
    from app.services.instance_service import InstanceService
    from app.services.ml_service import MlService

    async with session_factory() as s:
        res = await IngestionService(s, storage).ingest(build_dicom("DX", StudyInstanceUID="HMAP"))
        instance = await InstanceService(s).get_by_sop_uid(res.sop_instance_uid)
        storage.put_object(settings.s3_bucket_dicom, "heatmaps/h.png", b"img", "image/png")
        await MlService(s).store_result(
            instance_pk=instance.id,
            model_name="chexnet",
            model_version="v1",
            predictions={"Pneumonia": 0.8},
            top_label="Pneumonia",
            top_score=0.8,
            heatmap_key="heatmaps/h.png",
        )
        await s.commit()

    resp = await client.get(f"{PREFIX}/ml/results/HMAP", headers=rad_headers)
    assert resp.status_code == 200
    assert resp.json()[0]["heatmap_url"].startswith("http")
