#!/usr/bin/env python3
"""Train and export the procedural-terrain heightfield model.

Pipeline: heightmap dataset -> train a small generator -> export to ONNX. The engine
(engine/procgen) loads the exported .onnx via ONNX Runtime for runtime inference.
This is a runnable scaffold: the network/training loop are intentionally minimal and
marked with TODOs, but the CLI, config, and ONNX export are wired end-to-end.

Usage:
    python train_terrain.py --epochs 50 --resolution 256 \
        --dataset ./data/heightmaps --out ../../assets/models/terrain_model.onnx

Keep heavy ML deps OUT of the engine: only the exported ONNX ships.
"""
from __future__ import annotations

import argparse
import os
import sys
from dataclasses import dataclass


@dataclass
class TrainConfig:
    dataset_dir: str
    out_path: str
    resolution: int = 256
    epochs: int = 50
    batch_size: int = 16
    latent_dim: int = 64
    lr: float = 2e-4
    seed: int = 1337
    model_version: str = "v1"


def parse_args(argv: list[str]) -> TrainConfig:
    p = argparse.ArgumentParser(description="Train/export the terrain heightfield model.")
    p.add_argument("--dataset", default=os.environ.get("PROCGEN_DATASET_DIR", "./data/heightmaps"))
    p.add_argument("--out", default=os.environ.get("PROCGEN_MODEL_OUT",
                                                   "../../assets/models/terrain_model.onnx"))
    p.add_argument("--resolution", type=int, default=256)
    p.add_argument("--epochs", type=int, default=50)
    p.add_argument("--batch-size", type=int, default=16)
    p.add_argument("--latent-dim", type=int, default=64)
    p.add_argument("--lr", type=float, default=2e-4)
    p.add_argument("--seed", type=int, default=int(os.environ.get("PROCGEN_SEED", "1337")))
    p.add_argument("--model-version", default="v1")
    a = p.parse_args(argv)
    return TrainConfig(
        dataset_dir=a.dataset, out_path=a.out, resolution=a.resolution, epochs=a.epochs,
        batch_size=a.batch_size, latent_dim=a.latent_dim, lr=a.lr, seed=a.seed,
        model_version=a.model_version,
    )


def build_model(cfg: TrainConfig):
    """Create the generator network. TODO: replace with the real architecture."""
    import torch
    from torch import nn

    class TerrainGenerator(nn.Module):
        """Maps a conditioning vector (latent + tile coords + biome id) -> heightfield."""

        def __init__(self, latent_dim: int, resolution: int) -> None:
            super().__init__()
            self.resolution = resolution
            # TODO: convolutional upsampling generator. Placeholder MLP for the scaffold.
            self.net = nn.Sequential(
                nn.Linear(latent_dim + 3, 256),
                nn.ReLU(inplace=True),
                nn.Linear(256, resolution * resolution),
                nn.Sigmoid(),  # heights normalized to [0, 1]
            )

        def forward(self, cond: "torch.Tensor") -> "torch.Tensor":
            out = self.net(cond)
            return out.view(-1, 1, self.resolution, self.resolution)

    return TerrainGenerator(cfg.latent_dim, cfg.resolution)


def train(cfg: TrainConfig) -> None:
    import torch

    torch.manual_seed(cfg.seed)
    model = build_model(cfg)
    model.train()

    # TODO: load the real heightmap dataset from cfg.dataset_dir (DataLoader),
    #       define loss (e.g., L1 + adversarial), and run the optimizer loop.
    if not os.path.isdir(cfg.dataset_dir):
        print(f"[warn] dataset dir '{cfg.dataset_dir}' not found — running a no-op smoke train.")

    optimizer = torch.optim.Adam(model.parameters(), lr=cfg.lr)
    for epoch in range(cfg.epochs):
        # Placeholder step on random conditioning so the scaffold runs end-to-end.
        cond = torch.randn(cfg.batch_size, cfg.latent_dim + 3)
        optimizer.zero_grad()
        out = model(cond)
        loss = out.mean()  # TODO: real reconstruction/adversarial loss vs. dataset
        loss.backward()
        optimizer.step()
        if epoch % 10 == 0:
            print(f"[train] epoch {epoch:>4}/{cfg.epochs}  loss={loss.item():.5f}")

    export_onnx(model, cfg)


def export_onnx(model, cfg: TrainConfig) -> None:
    import torch

    os.makedirs(os.path.dirname(os.path.abspath(cfg.out_path)), exist_ok=True)
    model.eval()
    dummy = torch.randn(1, cfg.latent_dim + 3)
    torch.onnx.export(
        model,
        dummy,
        cfg.out_path,
        input_names=["conditioning"],
        output_names=["heightfield"],
        dynamic_axes={"conditioning": {0: "batch"}, "heightfield": {0: "batch"}},
        opset_version=17,
    )
    print(f"[export] wrote ONNX model -> {cfg.out_path} (version {cfg.model_version})")


def main() -> int:
    cfg = parse_args(sys.argv[1:])
    try:
        import torch  # noqa: F401
    except ImportError:
        print("error: PyTorch not installed. Run: pip install -r requirements.txt", file=sys.stderr)
        return 1
    train(cfg)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
