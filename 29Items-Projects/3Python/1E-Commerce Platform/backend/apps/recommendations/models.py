"""Recommendation models for E-Commerce Platform."""
from django.conf import settings
from django.db import models


class UserInteraction(models.Model):
    """Track user interactions with products for recommendations."""

    class InteractionType(models.TextChoices):
        VIEW = 'view', 'Viewed'
        ADD_TO_CART = 'cart', 'Added to Cart'
        PURCHASE = 'purchase', 'Purchased'
        WISHLIST = 'wishlist', 'Added to Wishlist'
        REVIEW = 'review', 'Reviewed'

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='interactions'
    )
    product = models.ForeignKey(
        'products.Product',
        on_delete=models.CASCADE,
        related_name='interactions'
    )
    interaction_type = models.CharField(
        max_length=20,
        choices=InteractionType.choices
    )

    # Implicit rating based on interaction type
    # view=1, cart=2, wishlist=3, purchase=4, review=5
    implicit_rating = models.FloatField(default=1.0)

    # Session tracking for anonymous users
    session_key = models.CharField(max_length=40, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = 'user interaction'
        verbose_name_plural = 'user interactions'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', 'product']),
            models.Index(fields=['interaction_type', 'created_at']),
        ]

    def save(self, *args, **kwargs):
        """Set implicit rating based on interaction type."""
        rating_map = {
            self.InteractionType.VIEW: 1.0,
            self.InteractionType.ADD_TO_CART: 2.0,
            self.InteractionType.WISHLIST: 3.0,
            self.InteractionType.PURCHASE: 4.0,
            self.InteractionType.REVIEW: 5.0,
        }
        self.implicit_rating = rating_map.get(self.interaction_type, 1.0)
        super().save(*args, **kwargs)


class ProductSimilarity(models.Model):
    """Pre-computed product similarities for faster recommendations."""

    product = models.ForeignKey(
        'products.Product',
        on_delete=models.CASCADE,
        related_name='similarities'
    )
    similar_product = models.ForeignKey(
        'products.Product',
        on_delete=models.CASCADE,
        related_name='reverse_similarities'
    )
    similarity_score = models.FloatField()

    # Algorithm used to compute similarity
    algorithm = models.CharField(max_length=50, default='collaborative_filtering')

    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'product similarity'
        verbose_name_plural = 'product similarities'
        unique_together = ['product', 'similar_product', 'algorithm']
        ordering = ['-similarity_score']


class RecommendationModel(models.Model):
    """Track recommendation model versions and performance."""

    name = models.CharField(max_length=100)
    version = models.CharField(max_length=50)
    algorithm = models.CharField(max_length=50)

    # Model file path
    model_path = models.CharField(max_length=500)

    # Training metrics
    training_samples = models.PositiveIntegerField(default=0)
    training_loss = models.FloatField(null=True, blank=True)
    validation_loss = models.FloatField(null=True, blank=True)

    # Performance metrics
    precision_at_k = models.FloatField(null=True, blank=True)
    recall_at_k = models.FloatField(null=True, blank=True)
    ndcg_at_k = models.FloatField(null=True, blank=True)

    is_active = models.BooleanField(default=False)

    created_at = models.DateTimeField(auto_now_add=True)
    trained_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = 'recommendation model'
        verbose_name_plural = 'recommendation models'
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.name} v{self.version}"
