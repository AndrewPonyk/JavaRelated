#!/usr/bin/env python3
"""Generate the tiny ONNX fixtures used by the ONNX Runtime backend tests.

    pip install onnx
    python scripts/make_test_onnx.py

Creates:
  tests/data/affine.onnx   -- output = input * 2 + 1   (shape [1,1,4,4])

The affine model is trivial to verify by hand, so the C++ test can assert the
ONNX Runtime session produced exactly the expected values (proving real
inference, not a stub).
"""
from __future__ import annotations

import pathlib

import onnx
from onnx import TensorProto, helper, numpy_helper
import numpy as np

OUT_DIR = pathlib.Path(__file__).resolve().parent.parent / "tests" / "data"


def build_affine() -> onnx.ModelProto:
    shape = [1, 1, 4, 4]
    inp = helper.make_tensor_value_info("input", TensorProto.FLOAT, shape)
    out = helper.make_tensor_value_info("output", TensorProto.FLOAT, shape)

    scale = numpy_helper.from_array(np.array(2.0, dtype=np.float32), name="scale")
    bias = numpy_helper.from_array(np.array(1.0, dtype=np.float32), name="bias")

    mul = helper.make_node("Mul", ["input", "scale"], ["scaled"])
    add = helper.make_node("Add", ["scaled", "bias"], ["output"])

    graph = helper.make_graph([mul, add], "affine", [inp], [out], [scale, bias])
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
    model.ir_version = 9  # compatible with onnxruntime 1.17.x
    onnx.checker.check_model(model)
    return model


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / "affine.onnx"
    onnx.save(build_affine(), str(path))
    print(f"wrote {path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
