"""Export a trained PyTorch model to ONNX, verify parity, optionally quantize.

The parity check (PyTorch logits vs ONNX logits within tolerance) is the single most
important guard against silent serving regressions. We export with a dynamic batch
axis so ``/classify/batch`` works.

Usage:
    python -m app.ml.export_onnx --weights artifacts/model.pt \
        --labels artifacts/labels.json --arch base --out artifacts/vit_clip_v1.onnx
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import torch

from app.ml.model import ViTConfig, ViTMultiLabelClassifier

OPSET = 17


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export model to ONNX with parity check")
    parser.add_argument("--weights", required=True)
    parser.add_argument("--labels", required=True)
    parser.add_argument("--arch", choices=["base", "small"], default="base")
    parser.add_argument("--out", default="artifacts/model.onnx")
    parser.add_argument("--atol", type=float, default=1e-3)
    parser.add_argument("--quantize", action="store_true", help="also emit an INT8 model")
    return parser.parse_args()


def _build_model(labels_path: str, arch: str, weights: str) -> ViTMultiLabelClassifier:
    labels = json.loads(Path(labels_path).read_text(encoding="utf-8"))
    config = ViTConfig.small() if arch == "small" else ViTConfig.base_patch32()
    model = ViTMultiLabelClassifier(num_labels=len(labels), config=config)
    model.load_state_dict(torch.load(weights, map_location="cpu"))
    model.eval()
    return model


def export(weights: str, labels_path: str, arch: str, out: str) -> None:
    model = _build_model(labels_path, arch, weights)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    dummy = torch.randn(1, 3, model.config.image_size, model.config.image_size)
    torch.onnx.export(
        model,
        (dummy,),
        out,
        input_names=["pixel_values"],
        output_names=["logits"],
        dynamic_axes={"pixel_values": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=OPSET,
    )
    print(f"[export] wrote {out}")


def verify_parity(weights: str, labels_path: str, arch: str, onnx_path: str, atol: float) -> None:
    """Assert PyTorch and ONNX produce matching logits within ``atol``."""
    import onnxruntime as ort

    model = _build_model(labels_path, arch, weights)
    sample = torch.randn(2, 3, model.config.image_size, model.config.image_size)
    with torch.no_grad():
        torch_logits = model(sample).numpy()

    session = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    onnx_logits = session.run(None, {"pixel_values": sample.numpy()})[0]

    np.testing.assert_allclose(torch_logits, onnx_logits, atol=atol, rtol=1e-3)
    print(f"[parity] OK within atol={atol}")


def quantize(onnx_path: str) -> str:
    """Dynamic INT8 quantization for smaller, faster CPU inference."""
    from onnxruntime.quantization import QuantType, quantize_dynamic

    quant_path = onnx_path.replace(".onnx", ".int8.onnx")
    quantize_dynamic(onnx_path, quant_path, weight_type=QuantType.QInt8)
    print(f"[quantize] wrote {quant_path}")
    return quant_path


def main() -> None:
    args = parse_args()
    export(args.weights, args.labels, args.arch, args.out)
    verify_parity(args.weights, args.labels, args.arch, args.out, args.atol)
    if args.quantize:
        quantize(args.out)


if __name__ == "__main__":
    main()
