"""Fine-tuning entrypoint (offline; never runs in the request path).

Examples:
    # Real data
    python -m app.ml.train --train data/train.jsonl --val data/val.jsonl \
        --labels data/labels.json --epochs 10 --out artifacts/

    # Synthetic smoke run (no data needed) with the small/fast architecture
    python -m app.ml.train --synthetic --arch small --epochs 3 --out artifacts/

Multi-label training uses BCEWithLogitsLoss. After training we calibrate per-label
decision thresholds on the validation set and save them next to the weights.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import torch
from torch import nn

from app.ml.dataset import DEFAULT_LABELS, build_dataloaders
from app.ml.metrics import calibrate_thresholds, macro_f1, sigmoid
from app.ml.model import ViTConfig, ViTMultiLabelClassifier


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fine-tune the ViT multi-label model")
    parser.add_argument("--train")
    parser.add_argument("--val")
    parser.add_argument("--labels")
    parser.add_argument("--synthetic", action="store_true", help="use synthetic data")
    parser.add_argument("--arch", choices=["base", "small"], default="base")
    parser.add_argument("--pretrained", action="store_true", help="init from CLIP weights")
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--lr", type=float, default=3e-4)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--out", default="artifacts")
    parser.add_argument("--seed", type=int, default=0)
    return parser.parse_args()


def load_labels(args: argparse.Namespace) -> list[str]:
    if args.labels:
        return json.loads(Path(args.labels).read_text(encoding="utf-8"))
    return DEFAULT_LABELS


@torch.no_grad()
def evaluate(model: nn.Module, loader, device: str) -> tuple[np.ndarray, np.ndarray]:
    """Return (y_true, scores) over the whole loader."""
    model.eval()
    all_true, all_scores = [], []
    for images, targets in loader:
        logits = model(images.to(device)).cpu().numpy()
        all_scores.append(sigmoid(logits))
        all_true.append(targets.numpy())
    return np.concatenate(all_true), np.concatenate(all_scores)


def train_model(args: argparse.Namespace) -> dict[str, object]:
    torch.manual_seed(args.seed)
    np.random.seed(args.seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"

    labels = load_labels(args)
    config = ViTConfig.small() if args.arch == "small" else ViTConfig.base_patch32()
    model = ViTMultiLabelClassifier(num_labels=len(labels), config=config).to(device)

    if args.pretrained and args.arch == "base":
        ok = model.load_pretrained_clip()
        print(f"[train] pretrained CLIP weights loaded: {ok}")

    train_dl, val_dl = build_dataloaders(
        label_vocab=labels,
        train_manifest=None if args.synthetic else args.train,
        val_manifest=None if args.synthetic else args.val,
        batch_size=args.batch_size,
        synthetic_train=getattr(args, "synthetic_train", 512),
        synthetic_val=getattr(args, "synthetic_val", 128),
    )

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)
    criterion = nn.BCEWithLogitsLoss()

    best_f1 = -1.0
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    weights_path = out_dir / "model.pt"

    for epoch in range(1, args.epochs + 1):
        model.train()
        running = 0.0
        for images, targets in train_dl:
            optimizer.zero_grad()
            logits = model(images.to(device))
            loss = criterion(logits, targets.to(device))
            loss.backward()
            optimizer.step()
            running += loss.item()

        y_true, scores = evaluate(model, val_dl, device)
        f1 = macro_f1(y_true, (scores >= 0.5).astype(int))
        avg_loss = running / max(len(train_dl), 1)
        print(f"[train] epoch {epoch}/{args.epochs} loss={avg_loss:.4f} val_macroF1={f1:.4f}")

        if f1 >= best_f1:
            best_f1 = f1
            torch.save(model.state_dict(), weights_path)

    # Calibrate thresholds on the validation set using the best model.
    model.load_state_dict(torch.load(weights_path, map_location=device))
    y_true, scores = evaluate(model, val_dl, device)
    thresholds = calibrate_thresholds(y_true, scores)
    final_f1 = macro_f1(y_true, (scores >= np.array(thresholds)).astype(int))

    (out_dir / "labels.json").write_text(json.dumps(labels, indent=2), encoding="utf-8")
    (out_dir / "thresholds.json").write_text(json.dumps(thresholds, indent=2), encoding="utf-8")
    (out_dir / "metadata.json").write_text(
        json.dumps(
            {"arch": args.arch, "num_labels": len(labels), "val_macro_f1": final_f1},
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"[train] done. calibrated val_macroF1={final_f1:.4f}; artifacts -> {out_dir}")
    return {"macro_f1": final_f1, "out_dir": str(out_dir), "labels": labels}


def main() -> None:
    train_model(parse_args())


if __name__ == "__main__":
    main()
