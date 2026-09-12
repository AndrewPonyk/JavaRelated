"""Inference entrypoints used by the ML severity microservice."""

import asyncio

from app.core.config import get_settings
from app.db.neo4j_client import get_driver
from app.ml.features import FeatureExtractor
from app.ml.model import SeverityModel
from app.models.common import Severity


def _classify(features: list[float]) -> tuple[Severity, float]:
    severity, confidence = SeverityModel.instance().predict(features)
    if confidence < get_settings().ml_confidence_floor:
        severity = Severity.UNKNOWN
    return severity, confidence


async def predict_severity(rxcui_a: str, rxcui_b: str) -> dict:
    extractor = FeatureExtractor(get_driver())
    features = await extractor.extract(rxcui_a, rxcui_b)
    severity, confidence = _classify(features.to_vector())
    return {
        "rxcui_a": rxcui_a,
        "rxcui_b": rxcui_b,
        "severity": severity.value,
        "confidence": round(confidence, 4),
        "mechanism": None,
    }


async def predict_severity_batch(pairs: list[tuple[str, str]]) -> list[dict]:
    if not pairs:
        return []
    return list(await asyncio.gather(*(predict_severity(a, b) for a, b in pairs)))
