"""Training pipeline for the collaborative-filtering model.

Runs on the dedicated `ml` Celery queue (nightly). Exports user/item interaction
data from PostgreSQL, builds dense index maps, trains a matrix-factorization
model, and saves a checkpoint (weights + id<->index maps) for serving.

torch/numpy are imported lazily inside functions so the module can be imported
without those heavy deps present.
"""

from __future__ import annotations

import logging

from django.conf import settings

logger = logging.getLogger(__name__)


def load_interactions():
    """Return (user_ids, item_ids, weights, user_map, item_map) from the DB.

    Maps raw user/product ids onto dense contiguous indices required by the
    embedding tables.
    """
    from apps.recommendations.models import Interaction

    rows = list(Interaction.objects.values_list("user_id", "product_id", "weight"))
    user_ids = sorted({r[0] for r in rows})
    item_ids = sorted({r[1] for r in rows})
    user_map = {uid: idx for idx, uid in enumerate(user_ids)}
    item_map = {pid: idx for idx, pid in enumerate(item_ids)}

    u = [user_map[r[0]] for r in rows]
    i = [item_map[r[1]] for r in rows]
    w = [float(r[2]) for r in rows]
    return u, i, w, user_map, item_map


def train_from_db(
    *,
    epochs: int = 10,
    lr: float = 1e-2,
    weight_decay: float = 1e-5,
    embedding_dim: int = 32,
):
    """Train end-to-end from the interaction table. Returns (model, blob)."""
    import torch
    from torch import nn, optim
    from torch.utils.data import DataLoader, TensorDataset

    from .model import MatrixFactorization

    u, i, w, user_map, item_map = load_interactions()
    if not u:
        raise ValueError("No interaction data to train on.")

    num_users, num_items = len(user_map), len(item_map)
    dataset = TensorDataset(
        torch.tensor(u, dtype=torch.long),
        torch.tensor(i, dtype=torch.long),
        # Implicit feedback: any interaction is a positive (label 1.0).
        torch.tensor([1.0] * len(u), dtype=torch.float32),
    )
    loader = DataLoader(dataset, batch_size=1024, shuffle=True)

    model = MatrixFactorization(num_users, num_items, embedding_dim)
    optimizer = optim.Adam(model.parameters(), lr=lr, weight_decay=weight_decay)
    loss_fn = nn.BCEWithLogitsLoss()

    model.train()
    for epoch in range(epochs):
        epoch_loss = 0.0
        for bu, bi, by in loader:
            optimizer.zero_grad()
            loss = loss_fn(model(bu, bi), by)
            loss.backward()
            optimizer.step()
            epoch_loss += loss.item()
        logger.info("reco.train.epoch", extra={"epoch": epoch, "loss": epoch_loss})

    blob = {
        "state_dict": model.state_dict(),
        "num_users": num_users,
        "num_items": num_items,
        "embedding_dim": embedding_dim,
        "user_id_to_index": user_map,
        "item_index_to_id": {idx: pid for pid, idx in item_map.items()},
    }
    return model, blob


def save_checkpoint(blob: dict, path: str | None = None) -> str:
    import os

    import torch

    path = path or settings.ML_MODEL_LOCAL_PATH
    os.makedirs(os.path.dirname(path), exist_ok=True)
    torch.save(blob, path)
    # In production also upload to s3://<bucket>/<ML_MODEL_S3_PREFIX> with a tag.
    return path
