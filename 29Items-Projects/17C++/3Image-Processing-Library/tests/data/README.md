# Test fixtures

Place small, version-controlled test assets here:

- `sample_frame.png` — a tiny image used by IO / blur tests.
- `tiny_detector.onnx` — a minimal model for the detection/pipeline tests.
- `*.golden` — reference outputs for golden comparisons.

Keep fixtures **small**. If any asset grows large (models, video clips), track
it with **Git LFS** instead of committing the binary directly.

The directory path is exposed to the test binary as the `IMGPROC_TEST_DATA_DIR`
compile definition (see `tests/CMakeLists.txt`).
