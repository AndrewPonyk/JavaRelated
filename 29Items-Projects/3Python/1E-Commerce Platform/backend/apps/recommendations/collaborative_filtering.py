"""Collaborative Filtering recommendation model using PyTorch."""
import os
from typing import List, Optional, Tuple

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset


class UserItemDataset(Dataset):
    """Dataset for user-item interactions."""

    def __init__(
        self,
        user_ids: np.ndarray,
        item_ids: np.ndarray,
        ratings: np.ndarray
    ):
        self.user_ids = torch.LongTensor(user_ids)
        self.item_ids = torch.LongTensor(item_ids)
        self.ratings = torch.FloatTensor(ratings)

    def __len__(self):
        return len(self.ratings)

    def __getitem__(self, idx):
        return self.user_ids[idx], self.item_ids[idx], self.ratings[idx]


class MatrixFactorization(nn.Module):
    """Matrix Factorization model for collaborative filtering."""

    def __init__(
        self,
        num_users: int,
        num_items: int,
        embedding_dim: int = 64,
        dropout: float = 0.2
    ):
        super().__init__()

        self.user_embedding = nn.Embedding(num_users, embedding_dim)
        self.item_embedding = nn.Embedding(num_items, embedding_dim)

        self.user_bias = nn.Embedding(num_users, 1)
        self.item_bias = nn.Embedding(num_items, 1)

        self.dropout = nn.Dropout(dropout)

        # Initialize embeddings
        nn.init.normal_(self.user_embedding.weight, std=0.01)
        nn.init.normal_(self.item_embedding.weight, std=0.01)
        nn.init.zeros_(self.user_bias.weight)
        nn.init.zeros_(self.item_bias.weight)

    def forward(self, user_ids: torch.Tensor, item_ids: torch.Tensor) -> torch.Tensor:
        user_emb = self.dropout(self.user_embedding(user_ids))
        item_emb = self.dropout(self.item_embedding(item_ids))

        user_b = self.user_bias(user_ids).squeeze()
        item_b = self.item_bias(item_ids).squeeze()

        # Dot product + biases
        interaction = (user_emb * item_emb).sum(dim=1)
        prediction = interaction + user_b + item_b

        return prediction


class NeuralCollaborativeFiltering(nn.Module):
    """Neural Collaborative Filtering model combining GMF and MLP."""

    def __init__(
        self,
        num_users: int,
        num_items: int,
        embedding_dim: int = 64,
        mlp_layers: List[int] = None,
        dropout: float = 0.2
    ):
        super().__init__()

        if mlp_layers is None:
            mlp_layers = [128, 64, 32]

        # GMF embeddings
        self.gmf_user_embedding = nn.Embedding(num_users, embedding_dim)
        self.gmf_item_embedding = nn.Embedding(num_items, embedding_dim)

        # MLP embeddings
        self.mlp_user_embedding = nn.Embedding(num_users, embedding_dim)
        self.mlp_item_embedding = nn.Embedding(num_items, embedding_dim)

        # MLP layers
        mlp_modules = []
        input_size = embedding_dim * 2

        for layer_size in mlp_layers:
            mlp_modules.append(nn.Linear(input_size, layer_size))
            mlp_modules.append(nn.ReLU())
            mlp_modules.append(nn.Dropout(dropout))
            input_size = layer_size

        self.mlp = nn.Sequential(*mlp_modules)

        # Final prediction layer
        self.output_layer = nn.Linear(embedding_dim + mlp_layers[-1], 1)

        self._init_weights()

    def _init_weights(self):
        for m in self.modules():
            if isinstance(m, nn.Embedding):
                nn.init.normal_(m.weight, std=0.01)
            elif isinstance(m, nn.Linear):
                nn.init.kaiming_normal_(m.weight)
                nn.init.zeros_(m.bias)

    def forward(self, user_ids: torch.Tensor, item_ids: torch.Tensor) -> torch.Tensor:
        # GMF part
        gmf_user_emb = self.gmf_user_embedding(user_ids)
        gmf_item_emb = self.gmf_item_embedding(item_ids)
        gmf_output = gmf_user_emb * gmf_item_emb

        # MLP part
        mlp_user_emb = self.mlp_user_embedding(user_ids)
        mlp_item_emb = self.mlp_item_embedding(item_ids)
        mlp_input = torch.cat([mlp_user_emb, mlp_item_emb], dim=1)
        mlp_output = self.mlp(mlp_input)

        # Combine and predict
        combined = torch.cat([gmf_output, mlp_output], dim=1)
        prediction = self.output_layer(combined).squeeze()

        return prediction


class CollaborativeFilteringTrainer:
    """Trainer for collaborative filtering models."""

    def __init__(
        self,
        model: nn.Module,
        learning_rate: float = 0.001,
        weight_decay: float = 1e-5,
        device: str = 'cpu'
    ):
        self.model = model.to(device)
        self.device = device
        self.optimizer = torch.optim.Adam(
            model.parameters(),
            lr=learning_rate,
            weight_decay=weight_decay
        )
        self.criterion = nn.MSELoss()

    def train_epoch(self, dataloader: DataLoader) -> float:
        """Train for one epoch."""
        self.model.train()
        total_loss = 0.0

        for user_ids, item_ids, ratings in dataloader:
            user_ids = user_ids.to(self.device)
            item_ids = item_ids.to(self.device)
            ratings = ratings.to(self.device)

            self.optimizer.zero_grad()

            predictions = self.model(user_ids, item_ids)
            loss = self.criterion(predictions, ratings)

            loss.backward()
            self.optimizer.step()

            total_loss += loss.item()

        return total_loss / len(dataloader)

    def evaluate(self, dataloader: DataLoader) -> float:
        """Evaluate model on validation data."""
        self.model.eval()
        total_loss = 0.0

        with torch.no_grad():
            for user_ids, item_ids, ratings in dataloader:
                user_ids = user_ids.to(self.device)
                item_ids = item_ids.to(self.device)
                ratings = ratings.to(self.device)

                predictions = self.model(user_ids, item_ids)
                loss = self.criterion(predictions, ratings)

                total_loss += loss.item()

        return total_loss / len(dataloader)

    def predict(
        self,
        user_id: int,
        item_ids: List[int]
    ) -> List[Tuple[int, float]]:
        """Predict ratings for a user on given items."""
        self.model.eval()

        user_tensor = torch.LongTensor([user_id] * len(item_ids)).to(self.device)
        item_tensor = torch.LongTensor(item_ids).to(self.device)

        with torch.no_grad():
            predictions = self.model(user_tensor, item_tensor)

        return list(zip(item_ids, predictions.cpu().numpy().tolist()))

    def get_top_k_items(
        self,
        user_id: int,
        all_item_ids: List[int],
        k: int = 10,
        exclude_items: Optional[List[int]] = None
    ) -> List[Tuple[int, float]]:
        """Get top-k item recommendations for a user."""
        if exclude_items:
            item_ids = [i for i in all_item_ids if i not in exclude_items]
        else:
            item_ids = all_item_ids

        predictions = self.predict(user_id, item_ids)
        predictions.sort(key=lambda x: x[1], reverse=True)

        return predictions[:k]

    def save_model(self, path: str) -> None:
        """Save model to disk."""
        torch.save({
            'model_state_dict': self.model.state_dict(),
            'optimizer_state_dict': self.optimizer.state_dict(),
        }, path)

    def load_model(self, path: str) -> None:
        """Load model from disk."""
        checkpoint = torch.load(path, map_location=self.device)
        self.model.load_state_dict(checkpoint['model_state_dict'])
        self.optimizer.load_state_dict(checkpoint['optimizer_state_dict'])


def compute_item_similarities(
    model: nn.Module,
    num_items: int,
    k: int = 50,
    device: str = 'cpu'
) -> dict:
    """Compute item-item similarities from learned embeddings."""
    model.eval()

    # Get item embeddings
    if hasattr(model, 'item_embedding'):
        item_embeddings = model.item_embedding.weight.detach()
    elif hasattr(model, 'gmf_item_embedding'):
        item_embeddings = model.gmf_item_embedding.weight.detach()
    else:
        raise ValueError("Model does not have item embeddings")

    item_embeddings = item_embeddings.to(device)

    # Normalize embeddings
    item_embeddings = item_embeddings / item_embeddings.norm(dim=1, keepdim=True)

    # Compute cosine similarities
    similarities = torch.mm(item_embeddings, item_embeddings.t())

    # Get top-k similar items for each item
    result = {}
    for item_id in range(num_items):
        sim_scores = similarities[item_id]
        sim_scores[item_id] = -1  # Exclude self

        top_k_values, top_k_indices = torch.topk(sim_scores, k)

        result[item_id] = list(zip(
            top_k_indices.cpu().numpy().tolist(),
            top_k_values.cpu().numpy().tolist()
        ))

    return result
