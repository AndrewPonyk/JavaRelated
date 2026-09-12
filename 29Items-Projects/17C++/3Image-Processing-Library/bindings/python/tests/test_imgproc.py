"""pytest suite for the imgproc Python bindings.

Run after building the wheel:
    pip install ./bindings/python
    pytest bindings/python/tests
"""
import numpy as np
import pytest

imgproc = pytest.importorskip("imgproc")


def _blob_frame(side=48):
    frame = np.zeros((side, side), dtype=np.uint8)
    frame[6:14, 6:14] = 255  # bright square
    return frame


def test_version_present():
    assert isinstance(imgproc.__version__, str)
    assert imgproc.__version__


def test_detect_finds_blob():
    det = imgproc.ObjectDetector(imgproc.DetectorConfig())
    detections = det.detect(_blob_frame())
    assert len(detections) == 1
    assert detections[0].label == "object"
    assert detections[0].score > 0.5


def test_detect_rejects_wrong_dtype():
    det = imgproc.ObjectDetector(imgproc.DetectorConfig())
    with pytest.raises(Exception):
        det.detect(np.zeros((10, 10), dtype=np.float32))


def test_tracker_assigns_and_keeps_ids():
    det = imgproc.ObjectDetector(imgproc.DetectorConfig())
    tracker = imgproc.Tracker()
    ids = set()
    for _ in range(4):
        tracks = tracker.update(det.detect(_blob_frame()))
        assert len(tracks) == 1
        ids.add(tracks[0].track_id)
    assert len(ids) == 1  # one stable identity across frames


def test_color_frame_supported():
    det = imgproc.ObjectDetector(imgproc.DetectorConfig())
    frame = np.zeros((48, 48, 3), dtype=np.uint8)
    frame[6:14, 6:14, :] = 255
    detections = det.detect(frame)
    assert len(detections) == 1
