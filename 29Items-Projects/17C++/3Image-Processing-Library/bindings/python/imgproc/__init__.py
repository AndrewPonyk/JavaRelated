"""imgproc — Python bindings for the CUDA-accelerated computer-vision library.

Example
-------
>>> import numpy as np
>>> import imgproc
>>> frame = np.zeros((48, 48), dtype=np.uint8)
>>> frame[6:14, 6:14] = 255                      # a bright square
>>> det = imgproc.ObjectDetector(imgproc.DetectorConfig())
>>> detections = det.detect(frame)
>>> tracker = imgproc.Tracker()
>>> tracks = tracker.update(detections)
>>> print(imgproc.__version__)
"""
from __future__ import annotations

from ._imgproc import (  # type: ignore[import-not-found]
    BBox,
    Detection,
    DetectorBackend,
    DetectorConfig,
    ObjectDetector,
    Track,
    Tracker,
    TrackerConfig,
    __version__,
)

__all__ = [
    "BBox",
    "Detection",
    "DetectorBackend",
    "DetectorConfig",
    "ObjectDetector",
    "Track",
    "Tracker",
    "TrackerConfig",
    "__version__",
]
