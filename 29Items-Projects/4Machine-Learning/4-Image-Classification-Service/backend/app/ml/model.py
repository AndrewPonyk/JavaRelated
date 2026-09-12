"""Model definition: a CLIP-style Vision Transformer + multi-label head.

This is a self-contained PyTorch implementation of the CLIP ViT architecture
(patch embedding -> transformer encoder -> CLS token -> linear head). It needs only
``torch`` (no ``transformers`` download), so training, ONNX export, and parity checks
run fully offline and deterministically.

Transfer learning: ``load_pretrained_clip()`` is an optional hook that copies weights
from a HuggingFace CLIP checkpoint when ``transformers`` + network are available; the
architecture here mirrors ``openai/clip-vit-base-patch32`` so the weights line up.

Multi-label classification => the head emits one logit per category; apply sigmoid +
per-label thresholds downstream (never softmax/argmax).
"""

from __future__ import annotations

from dataclasses import dataclass

import torch
from torch import nn


@dataclass
class ViTConfig:
    """Vision Transformer hyper-parameters."""

    image_size: int = 224
    patch_size: int = 32
    in_channels: int = 3
    hidden_size: int = 768
    num_layers: int = 12
    num_heads: int = 12
    mlp_ratio: float = 4.0
    dropout: float = 0.0

    @property
    def num_patches(self) -> int:
        grid = self.image_size // self.patch_size
        return grid * grid

    @classmethod
    def base_patch32(cls) -> ViTConfig:
        """CLIP ViT-B/32 configuration."""
        return cls()

    @classmethod
    def small(cls) -> ViTConfig:
        """A small, fast config for local demos, CI, and tests."""
        return cls(patch_size=32, hidden_size=128, num_layers=2, num_heads=4)


class PatchEmbedding(nn.Module):
    """Split an image into patches and linearly embed them (Conv2d, CLIP-style)."""

    def __init__(self, config: ViTConfig) -> None:
        super().__init__()
        self.projection = nn.Conv2d(
            config.in_channels,
            config.hidden_size,
            kernel_size=config.patch_size,
            stride=config.patch_size,
        )
        self.class_embedding = nn.Parameter(torch.randn(config.hidden_size))
        self.position_embedding = nn.Parameter(
            torch.randn(config.num_patches + 1, config.hidden_size)
        )

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        batch = pixel_values.shape[0]
        patches = self.projection(pixel_values)  # (B, C, gh, gw)
        patches = patches.flatten(2).transpose(1, 2)  # (B, num_patches, C)
        cls = self.class_embedding.expand(batch, 1, -1)
        tokens = torch.cat([cls, patches], dim=1)
        return tokens + self.position_embedding


class EncoderBlock(nn.Module):
    """Pre-norm transformer block (MHSA + MLP), matching CLIP's residual design."""

    def __init__(self, config: ViTConfig) -> None:
        super().__init__()
        self.ln_1 = nn.LayerNorm(config.hidden_size)
        self.attn = nn.MultiheadAttention(
            config.hidden_size, config.num_heads, dropout=config.dropout, batch_first=True
        )
        self.ln_2 = nn.LayerNorm(config.hidden_size)
        hidden_mlp = int(config.hidden_size * config.mlp_ratio)
        self.mlp = nn.Sequential(
            nn.Linear(config.hidden_size, hidden_mlp),
            nn.GELU(),
            nn.Dropout(config.dropout),
            nn.Linear(hidden_mlp, config.hidden_size),
            nn.Dropout(config.dropout),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        normed = self.ln_1(x)
        attn_out, _ = self.attn(normed, normed, normed, need_weights=False)
        x = x + attn_out
        x = x + self.mlp(self.ln_2(x))
        return x


class ViTMultiLabelClassifier(nn.Module):
    """CLIP-style ViT encoder with a multi-label linear head."""

    def __init__(self, num_labels: int, config: ViTConfig | None = None) -> None:
        super().__init__()
        self.config = config or ViTConfig.base_patch32()
        self.num_labels = num_labels

        self.embeddings = PatchEmbedding(self.config)
        self.pre_ln = nn.LayerNorm(self.config.hidden_size)
        self.blocks = nn.ModuleList(
            [EncoderBlock(self.config) for _ in range(self.config.num_layers)]
        )
        self.post_ln = nn.LayerNorm(self.config.hidden_size)
        self.head = nn.Linear(self.config.hidden_size, num_labels)

        self.apply(self._init_weights)

    @staticmethod
    def _init_weights(module: nn.Module) -> None:
        if isinstance(module, nn.Linear):
            nn.init.trunc_normal_(module.weight, std=0.02)
            if module.bias is not None:
                nn.init.zeros_(module.bias)
        elif isinstance(module, nn.LayerNorm):
            nn.init.ones_(module.weight)
            nn.init.zeros_(module.bias)

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        """Return raw logits of shape (batch, num_labels). Apply sigmoid downstream."""
        x = self.embeddings(pixel_values)
        x = self.pre_ln(x)
        for block in self.blocks:
            x = block(x)
        x = self.post_ln(x)
        cls_token = x[:, 0]  # CLS representation
        return self.head(cls_token)

    def freeze_backbone(self) -> None:
        """Freeze everything except the classification head (linear-probe transfer)."""
        for name, param in self.named_parameters():
            param.requires_grad = name.startswith("head.")

    def load_pretrained_clip(self, backbone_name: str = "openai/clip-vit-base-patch32") -> bool:
        """Best-effort: copy CLIP vision weights from HuggingFace into this model.

        Returns True on success, False if ``transformers`` is unavailable or the
        download fails (in which case the model keeps its random initialization).
        Only runs for a base_patch32-shaped model.
        """
        try:
            from transformers import CLIPVisionModel
        except Exception:
            return False
        try:
            clip = CLIPVisionModel.from_pretrained(backbone_name)
        except Exception:
            return False

        vision = clip.vision_model
        with torch.no_grad():
            self.embeddings.projection.weight.copy_(vision.embeddings.patch_embedding.weight)
            self.embeddings.class_embedding.copy_(vision.embeddings.class_embedding)
            self.embeddings.position_embedding.copy_(vision.embeddings.position_embedding.weight)
            self.pre_ln.weight.copy_(vision.pre_layrnorm.weight)
            self.pre_ln.bias.copy_(vision.pre_layrnorm.bias)
            for dst, src in zip(self.blocks, vision.encoder.layers, strict=False):
                dst.ln_1.weight.copy_(src.layer_norm1.weight)
                dst.ln_1.bias.copy_(src.layer_norm1.bias)
                dst.ln_2.weight.copy_(src.layer_norm2.weight)
                dst.ln_2.bias.copy_(src.layer_norm2.bias)
                # Pack q/k/v into the in_proj of nn.MultiheadAttention.
                qw = src.self_attn.q_proj.weight
                kw = src.self_attn.k_proj.weight
                vw = src.self_attn.v_proj.weight
                dst.attn.in_proj_weight.copy_(torch.cat([qw, kw, vw], dim=0))
                qb = src.self_attn.q_proj.bias
                kb = src.self_attn.k_proj.bias
                vb = src.self_attn.v_proj.bias
                dst.attn.in_proj_bias.copy_(torch.cat([qb, kb, vb], dim=0))
                dst.attn.out_proj.weight.copy_(src.self_attn.out_proj.weight)
                dst.attn.out_proj.bias.copy_(src.self_attn.out_proj.bias)
                dst.mlp[0].weight.copy_(src.mlp.fc1.weight)
                dst.mlp[0].bias.copy_(src.mlp.fc1.bias)
                dst.mlp[3].weight.copy_(src.mlp.fc2.weight)
                dst.mlp[3].bias.copy_(src.mlp.fc2.bias)
            self.post_ln.weight.copy_(vision.post_layernorm.weight)
            self.post_ln.bias.copy_(vision.post_layernorm.bias)
        return True
