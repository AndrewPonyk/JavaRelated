"""Datasets and dataloaders for multi-label product images.

Two dataset sources:
- ``ProductImageDataset`` reads a JSONL manifest of real images + labels.
- ``SyntheticProductDataset`` generates colored shapes on the fly so the training
  pipeline, ONNX export, and tests run end-to-end with no external data.

Both reuse ``app.services.preprocessing`` so training and serving share an identical
transform (the #1 guard against train/serve skew).
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from torch.utils.data import DataLoader, Dataset

from app.services import preprocessing

# Default e-commerce taxonomy used by the demo/synthetic pipeline.
DEFAULT_LABELS = [
    "electronics",
    "clothing",
    "home_garden",
    "toys",
    "sports",
    "beauty",
    "books",
    "footwear",
]


def _multi_hot(labels: list[str], label_to_idx: dict[str, int]) -> torch.Tensor:
    vec = torch.zeros(len(label_to_idx), dtype=torch.float32)
    for name in labels:
        idx = label_to_idx.get(name)
        if idx is not None:
            vec[idx] = 1.0
    return vec


class ProductImageDataset(Dataset):
    """Loads (image, multi-hot label vector) pairs from a JSONL manifest.

    Each manifest line: ``{"image_path": "...", "labels": ["electronics", ...]}``.
    """

    def __init__(self, manifest_path: str | Path, label_vocab: list[str]) -> None:
        self.label_vocab = label_vocab
        self.label_to_idx = {name: i for i, name in enumerate(label_vocab)}
        self.samples: list[tuple[str, list[str]]] = []
        path = Path(manifest_path)
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                row = json.loads(line)
                self.samples.append((row["image_path"], list(row.get("labels", []))))

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> tuple[torch.Tensor, torch.Tensor]:
        image_path, labels = self.samples[idx]
        image = Image.open(image_path).convert("RGB")
        tensor = torch.from_numpy(preprocessing.preprocess(image)).squeeze(0)
        return tensor, _multi_hot(labels, self.label_to_idx)


class SyntheticProductDataset(Dataset):
    """Deterministic synthetic dataset: each label maps to a color/shape signature.

    Lets the whole training + export pipeline run without downloading real data, and
    is learnable enough to produce a non-trivial model for smoke tests.
    """

    def __init__(self, size: int, label_vocab: list[str], seed: int = 0) -> None:
        self.size = size
        self.label_vocab = label_vocab
        self.num_labels = len(label_vocab)
        self.rng = np.random.default_rng(seed)
        # Pre-draw a stable label assignment per sample (1-3 labels each).
        self.assignments = [
            sorted(self.rng.choice(self.num_labels, size=self.rng.integers(1, 4), replace=False))
            for _ in range(size)
        ]

    def __len__(self) -> int:
        return self.size

    def __getitem__(self, idx: int) -> tuple[torch.Tensor, torch.Tensor]:
        active = self.assignments[idx]
        target = torch.zeros(self.num_labels, dtype=torch.float32)
        # Build an image whose channel intensities encode the active labels.
        img = np.full(
            (preprocessing.IMAGE_SIZE, preprocessing.IMAGE_SIZE, 3), 0.1, dtype=np.float32
        )
        for label in active:
            target[label] = 1.0
            channel = label % 3
            band = label % 4
            start = band * (preprocessing.IMAGE_SIZE // 4)
            end = start + (preprocessing.IMAGE_SIZE // 4)
            img[start:end, :, channel] += 0.8
        img = np.clip(img, 0, 1)
        img = (img - preprocessing.CLIP_MEAN) / preprocessing.CLIP_STD
        tensor = torch.from_numpy(img.transpose(2, 0, 1).astype(np.float32))
        return tensor, target


def build_dataloaders(
    label_vocab: list[str],
    train_manifest: str | None = None,
    val_manifest: str | None = None,
    batch_size: int = 32,
    synthetic_train: int = 512,
    synthetic_val: int = 128,
) -> tuple[DataLoader, DataLoader]:
    """Build train/val loaders. Falls back to synthetic data when no manifest given."""
    if train_manifest and val_manifest:
        train_ds: Dataset = ProductImageDataset(train_manifest, label_vocab)
        val_ds: Dataset = ProductImageDataset(val_manifest, label_vocab)
    else:
        train_ds = SyntheticProductDataset(synthetic_train, label_vocab, seed=1)
        val_ds = SyntheticProductDataset(synthetic_val, label_vocab, seed=2)
    train_dl = DataLoader(train_ds, batch_size=batch_size, shuffle=True)
    val_dl = DataLoader(val_ds, batch_size=batch_size, shuffle=False)
    return train_dl, val_dl
