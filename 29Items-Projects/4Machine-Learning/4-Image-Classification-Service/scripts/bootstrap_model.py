"""Produce a deployable model artifact so the service boots green offline.

Runs a short synthetic training run with the small architecture, then exports to ONNX
with a parity check. Writes model.onnx, labels.json, thresholds.json into --out.

This is the artifact-build step referenced by ARCHITECTURE.md. It needs torch (it is a
build-time tool), NOT part of the slim serving image.

Usage:
    python scripts/bootstrap_model.py --out models --arch small --epochs 2
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Allow running as a standalone script (add backend/ to the path).
BACKEND = Path(__file__).resolve().parents[1] / "backend"
if str(BACKEND) not in sys.path:
    sys.path.insert(0, str(BACKEND))

from app.ml.export_onnx import export, verify_parity  # noqa: E402
from app.ml.train import train_model  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="Bootstrap a deployable ONNX model")
    parser.add_argument("--out", default="models")
    parser.add_argument("--arch", choices=["base", "small"], default="small")
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--model-name", default="vit_clip_v1.onnx")
    args = parser.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    train_args = argparse.Namespace(
        train=None,
        val=None,
        labels=None,
        synthetic=True,
        arch=args.arch,
        pretrained=False,
        epochs=args.epochs,
        lr=3e-4,
        batch_size=32,
        out=str(out_dir),
        seed=0,
    )
    train_model(train_args)

    onnx_path = str(out_dir / args.model_name)
    weights = str(out_dir / "model.pt")
    labels = str(out_dir / "labels.json")
    export(weights, labels, args.arch, onnx_path)
    verify_parity(weights, labels, args.arch, onnx_path, atol=1e-3)
    print(f"[bootstrap] model ready at {onnx_path}")


if __name__ == "__main__":
    main()
