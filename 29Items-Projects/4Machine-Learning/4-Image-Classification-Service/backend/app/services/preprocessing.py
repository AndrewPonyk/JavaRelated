"""Image preprocessing.

CRITICAL: this exact transform must match what was used during training (CLIP
mean/std, resize, center-crop). Import and reuse this module from the training
pipeline to guarantee parity — mismatched preprocessing is the #1 source of silent
accuracy loss in production.
"""

from __future__ import annotations

import hashlib
import io

import numpy as np
from PIL import Image

# CLIP ViT-B/32 preprocessing constants.
IMAGE_SIZE = 224
CLIP_MEAN = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
CLIP_STD = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)

# Guard against decompression bombs.
Image.MAX_IMAGE_PIXELS = 50_000_000


class UnsupportedImageError(ValueError):
    """Raised when an upload cannot be decoded as a supported image."""


def decode_image(raw: bytes) -> Image.Image:
    """Decode raw bytes into an RGB PIL image, or raise UnsupportedImageError."""
    try:
        image = Image.open(io.BytesIO(raw))
        image.load()
        return image.convert("RGB")
    except Exception as exc:  # noqa: BLE001 - normalize all decode failures
        raise UnsupportedImageError("Could not decode image") from exc


def preprocess(image: Image.Image) -> np.ndarray:
    """Resize, center-crop, normalize. Returns NCHW float32 tensor of shape (1,3,H,W)."""
    # Resize shortest side to IMAGE_SIZE, then center-crop to a square.
    image = image.resize((IMAGE_SIZE, IMAGE_SIZE), Image.Resampling.BICUBIC)
    arr = np.asarray(image, dtype=np.float32) / 255.0  # HWC, [0,1]
    arr = (arr - CLIP_MEAN) / CLIP_STD
    arr = arr.transpose(2, 0, 1)  # CHW
    return arr[np.newaxis, ...].astype(np.float32)  # NCHW


def content_hash(tensor: np.ndarray) -> str:
    """SHA-256 of the *normalized* tensor.

    Hashing post-normalization means visually identical re-uploads (different file
    names / encodings, same pixels after resize) collide in the cache.
    """
    return hashlib.sha256(tensor.tobytes()).hexdigest()
