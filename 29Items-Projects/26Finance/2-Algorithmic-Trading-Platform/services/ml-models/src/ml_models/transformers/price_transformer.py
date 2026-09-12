"""Transformer encoder for short-horizon price-direction prediction.

Input: a window of normalized OHLCV + engineered features.
Output: a probability distribution over {down, flat, up} for the next horizon.

This is a research stub demonstrating the shape/interface. Training data assembly,
proper feature scaling, walk-forward validation, and hyperparameter search are TODO.
"""

from __future__ import annotations

from dataclasses import dataclass

try:
    import torch
    from torch import nn
except ImportError as exc:  # pragma: no cover
    raise ImportError("Install torch to use ml-models (see requirements.txt).") from exc


@dataclass(frozen=True)
class ModelConfig:
    n_features: int = 16
    seq_len: int = 64
    d_model: int = 128
    n_heads: int = 8
    n_layers: int = 4
    dropout: float = 0.1
    n_classes: int = 3  # down / flat / up


class PositionalEncoding(nn.Module):
    """Standard sinusoidal positional encoding."""

    def __init__(self, d_model: int, max_len: int = 512) -> None:
        super().__init__()
        pos = torch.arange(max_len).unsqueeze(1)
        div = torch.exp(torch.arange(0, d_model, 2) * (-torch.log(torch.tensor(10000.0)) / d_model))
        pe = torch.zeros(max_len, d_model)
        pe[:, 0::2] = torch.sin(pos * div)
        pe[:, 1::2] = torch.cos(pos * div)
        self.register_buffer("pe", pe.unsqueeze(0))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return x + self.pe[:, : x.size(1)]


class PriceTransformer(nn.Module):
    """Encoder-only transformer that classifies next-horizon price direction."""

    def __init__(self, cfg: ModelConfig) -> None:
        super().__init__()
        self.cfg = cfg
        self.input_proj = nn.Linear(cfg.n_features, cfg.d_model)
        self.pos_enc = PositionalEncoding(cfg.d_model, max_len=cfg.seq_len)
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=cfg.d_model,
            nhead=cfg.n_heads,
            dim_feedforward=cfg.d_model * 4,
            dropout=cfg.dropout,
            batch_first=True,
        )
        self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=cfg.n_layers)
        self.head = nn.Linear(cfg.d_model, cfg.n_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (batch, seq_len, n_features)
        h = self.pos_enc(self.input_proj(x))
        h = self.encoder(h)
        pooled = h[:, -1, :]  # last-step representation
        return self.head(pooled)  # logits (batch, n_classes)


# TODO(phase-2): training loop with walk-forward CV, class weighting for imbalance,
#                early stopping on validation Sharpe (not just accuracy), export to ONNX.
