"""Collaborative-filtering model — Matrix Factorization in PyTorch.

Learns a low-dimensional embedding per user and per item; the predicted
affinity of a user for an item is the dot product of their embeddings plus
bias terms. This is the classic implicit-feedback recommender that powers the
"recommended for you" rail.
"""

from __future__ import annotations

import torch
from torch import nn


class MatrixFactorization(nn.Module):
    def __init__(self, num_users: int, num_items: int, embedding_dim: int = 64) -> None:
        super().__init__()
        self.user_embedding = nn.Embedding(num_users, embedding_dim)
        self.item_embedding = nn.Embedding(num_items, embedding_dim)
        self.user_bias = nn.Embedding(num_users, 1)
        self.item_bias = nn.Embedding(num_items, 1)
        self.global_bias = nn.Parameter(torch.zeros(1))

        # Small random init keeps early gradients stable.
        nn.init.normal_(self.user_embedding.weight, std=0.01)
        nn.init.normal_(self.item_embedding.weight, std=0.01)

    def forward(self, user_ids: torch.Tensor, item_ids: torch.Tensor) -> torch.Tensor:
        u = self.user_embedding(user_ids)
        i = self.item_embedding(item_ids)
        dot = (u * i).sum(dim=1)
        return (
            dot
            + self.user_bias(user_ids).squeeze(1)
            + self.item_bias(item_ids).squeeze(1)
            + self.global_bias
        )

    @torch.no_grad()
    def recommend(self, user_id: int, top_k: int = 10) -> list[int]:
        """Return the top-k item indices for a single user."""
        self.eval()
        user = torch.tensor([user_id])
        all_items = torch.arange(self.item_embedding.num_embeddings)
        scores = self.forward(user.repeat(len(all_items)), all_items)
        top = torch.topk(scores, k=min(top_k, len(all_items))).indices
        return top.tolist()
