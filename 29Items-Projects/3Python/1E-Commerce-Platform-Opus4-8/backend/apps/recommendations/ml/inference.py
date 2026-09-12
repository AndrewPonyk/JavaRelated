"""Serving-side inference.

Per ARCHITECTURE §2.4, recommendations are *precomputed* and cached in Redis;
the request path never runs a forward pass. This module loads the current
checkpoint and is invoked by the nightly batch task to refresh per-user caches.

torch is imported lazily inside the functions that need it, so importing this
module (e.g. from the request-serving views) never requires torch.
"""

from __future__ import annotations

from django.conf import settings
from django.core.cache import cache

CACHE_KEY_TEMPLATE = "reco:user:{user_id}:v1"
CACHE_TTL = 60 * 60 * 24  # 24h; refreshed nightly.

# Module-level handle so a loaded model is reused across calls in one process.
_MODEL = None
_INDEX_MAPS: dict | None = None


def load_checkpoint(path: str | None = None):
    """Load model weights + id<->index maps from a saved checkpoint."""
    import torch

    from .model import MatrixFactorization

    path = path or settings.ML_MODEL_LOCAL_PATH
    blob = torch.load(path, map_location="cpu")
    model = MatrixFactorization(
        num_users=blob["num_users"],
        num_items=blob["num_items"],
        embedding_dim=blob["embedding_dim"],
    )
    model.load_state_dict(blob["state_dict"])
    model.eval()
    return model, blob["user_id_to_index"], blob["item_index_to_id"]


def _ensure_loaded() -> bool:
    global _MODEL, _INDEX_MAPS
    if _MODEL is not None:
        return True
    try:
        model, user_map, item_map = load_checkpoint()
    except (FileNotFoundError, ImportError):
        # No checkpoint yet, or torch unavailable -> serve cold-start fallback.
        return False
    _MODEL = model
    _INDEX_MAPS = {"user": user_map, "item": item_map}
    return True


def precompute_for_user(user_id: int, top_k: int = 10) -> list[int]:
    """Compute and cache top-k *product ids* for a user. Returns the ids."""
    if not _ensure_loaded():
        return []
    assert _INDEX_MAPS is not None and _MODEL is not None
    user_index = _INDEX_MAPS["user"].get(user_id)
    if user_index is None:
        return []  # cold-start user — handled by the view's fallback.
    item_indices = _MODEL.recommend(user_index, top_k=top_k)
    product_ids = [_INDEX_MAPS["item"][i] for i in item_indices]
    cache.set(CACHE_KEY_TEMPLATE.format(user_id=user_id), product_ids, CACHE_TTL)
    return product_ids


def get_cached_recommendations(user_id: int) -> list[int] | None:
    """Hot path used by the API view — a single Redis read (no torch needed)."""
    return cache.get(CACHE_KEY_TEMPLATE.format(user_id=user_id))
