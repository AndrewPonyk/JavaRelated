"""Recommendation-related Celery tasks."""
import os
from datetime import timedelta

from celery import shared_task
from django.conf import settings
from django.utils import timezone


@shared_task(bind=True, soft_time_limit=7200)
def train_recommendation_model(self):
    """Train collaborative filtering recommendation model."""
    import numpy as np
    import torch
    from torch.utils.data import DataLoader

    from apps.recommendations.collaborative_filtering import (
        CollaborativeFilteringTrainer,
        NeuralCollaborativeFiltering,
        UserItemDataset,
        compute_item_similarities,
    )
    from apps.recommendations.models import (
        ProductSimilarity,
        RecommendationModel,
        UserInteraction,
    )

    # Fetch interaction data
    interactions = UserInteraction.objects.filter(
        user__isnull=False
    ).values('user_id', 'product_id', 'implicit_rating')

    if interactions.count() < 100:
        return {'status': 'skipped', 'reason': 'Not enough interactions'}

    # Create user and item mappings
    user_ids = list(set(i['user_id'] for i in interactions))
    item_ids = list(set(str(i['product_id']) for i in interactions))

    user_to_idx = {uid: idx for idx, uid in enumerate(user_ids)}
    item_to_idx = {iid: idx for idx, iid in enumerate(item_ids)}
    idx_to_item = {idx: iid for iid, idx in item_to_idx.items()}

    # Prepare training data
    users = np.array([user_to_idx[i['user_id']] for i in interactions])
    items = np.array([item_to_idx[str(i['product_id'])] for i in interactions])
    ratings = np.array([i['implicit_rating'] for i in interactions])

    # Normalize ratings
    ratings = (ratings - ratings.min()) / (ratings.max() - ratings.min() + 1e-8)

    # Split data
    split_idx = int(len(ratings) * 0.8)
    train_dataset = UserItemDataset(users[:split_idx], items[:split_idx], ratings[:split_idx])
    val_dataset = UserItemDataset(users[split_idx:], items[split_idx:], ratings[split_idx:])

    train_loader = DataLoader(train_dataset, batch_size=256, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=256)

    # Initialize model
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    model = NeuralCollaborativeFiltering(
        num_users=len(user_ids),
        num_items=len(item_ids),
        embedding_dim=64,
        mlp_layers=[128, 64, 32]
    )

    trainer = CollaborativeFilteringTrainer(model, learning_rate=0.001, device=device)

    # Training loop
    best_val_loss = float('inf')
    for epoch in range(20):
        train_loss = trainer.train_epoch(train_loader)
        val_loss = trainer.evaluate(val_loader)

        if val_loss < best_val_loss:
            best_val_loss = val_loss

    # Save model
    model_dir = os.path.join(settings.BASE_DIR, 'ml_models')
    os.makedirs(model_dir, exist_ok=True)

    version = timezone.now().strftime('%Y%m%d_%H%M%S')
    model_path = os.path.join(model_dir, f'ncf_model_{version}.pt')
    trainer.save_model(model_path)

    # Compute item similarities and store
    similarities = compute_item_similarities(model, len(item_ids), k=50, device=device)

    # Store similarities in database
    ProductSimilarity.objects.filter(algorithm='collaborative_filtering').delete()

    similarity_objects = []
    for item_idx, similar_items in similarities.items():
        product_id = idx_to_item[item_idx]
        for similar_idx, score in similar_items:
            if similar_idx in idx_to_item:
                similarity_objects.append(ProductSimilarity(
                    product_id=product_id,
                    similar_product_id=idx_to_item[similar_idx],
                    similarity_score=score,
                    algorithm='collaborative_filtering'
                ))

    ProductSimilarity.objects.bulk_create(similarity_objects, batch_size=1000)

    # Record model metadata
    RecommendationModel.objects.filter(is_active=True).update(is_active=False)
    RecommendationModel.objects.create(
        name='Neural Collaborative Filtering',
        version=version,
        algorithm='ncf',
        model_path=model_path,
        training_samples=len(ratings),
        training_loss=train_loss,
        validation_loss=best_val_loss,
        is_active=True,
        trained_at=timezone.now()
    )

    return {
        'status': 'success',
        'version': version,
        'training_samples': len(ratings),
        'validation_loss': best_val_loss
    }


@shared_task(bind=True, soft_time_limit=600)
def update_trending_products(self):
    """Update trending products cache."""
    from apps.recommendations.services import RecommendationService

    # Pre-compute trending products for different time windows
    for days in [1, 7, 30]:
        for limit in [10, 20, 50]:
            RecommendationService.get_trending_products(limit=limit, days=days)

    return {'status': 'success'}


@shared_task(bind=True, soft_time_limit=300)
def invalidate_stale_recommendations(self):
    """Clean up old user interaction data."""
    from apps.recommendations.models import UserInteraction

    # Delete interactions older than 90 days
    cutoff = timezone.now() - timedelta(days=90)
    deleted, _ = UserInteraction.objects.filter(created_at__lt=cutoff).delete()

    return {'deleted_interactions': deleted}
