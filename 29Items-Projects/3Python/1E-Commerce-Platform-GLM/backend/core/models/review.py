"""
Review Models

Models for product reviews and ratings.
"""

from django.db import models
from django.core.cache import cache
from django.core.exceptions import ValidationError


class Review(models.Model):
    """
    Product review and rating.
    """

    class Meta:
        db_table = "reviews"
        verbose_name = "Review"
        verbose_name_plural = "Reviews"
        ordering = ["-created_at"]
        unique_together = [["user", "product", "order"]]

    # Rating (1-5 stars)
    RATING_CHOICES = [
        (1, "1 Star - Poor"),
        (2, "2 Stars - Fair"),
        (3, "3 Stars - Good"),
        (4, "4 Stars - Very Good"),
        (5, "5 Stars - Excellent"),
    ]

    user = models.ForeignKey(
        "User",
        on_delete=models.CASCADE,
        related_name="reviews",
    )
    product = models.ForeignKey(
        "Product",
        on_delete=models.CASCADE,
        related_name="reviews",
    )
    order = models.ForeignKey(
        "Order",
        on_delete=models.CASCADE,
        related_name="reviews",
        null=True,
        blank=True,
    )

    rating = models.PositiveIntegerField(choices=RATING_CHOICES)
    title = models.CharField(max_length=255)
    comment = models.TextField()

    # Helpful votes
    helpful_count = models.PositiveIntegerField(default=0)
    not_helpful_count = models.PositiveIntegerField(default=0)

    # Moderation
    is_verified_purchase = models.BooleanField(default=False)
    is_approved = models.BooleanField(default=True)
    is_flagged = models.BooleanField(default=False)

    # Admin response
    admin_response = models.TextField(blank=True, null=True)
    admin_response_date = models.DateTimeField(blank=True, null=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"{self.user.email} - {self.product.name} ({self.rating}/5)"

    def clean(self):
        """Validate review can only be created after order is delivered."""
        if self.order and self.order.status not in ["delivered", "completed"]:
            raise ValidationError("Reviews can only be created for delivered orders")

    def save(self, *args, **kwargs):
        """Override save to update product ratings."""
        super().save(*args, **kwargs)
        self._update_product_rating()

    def delete(self, *args, **kwargs):
        """Override delete to update product ratings."""
        product = self.product
        super().delete(*args, **kwargs)
        self._update_product_rating_for_deletion(product)

    def _update_product_rating(self):
        """Update product's average rating and review count."""
        from decimal import Decimal

        product = self.product
        reviews = product.reviews.filter(is_approved=True)

        if reviews.exists():
            total = sum(r.rating for r in reviews)
            avg = Decimal(total) / Decimal(reviews.count())
            product.average_rating = avg.quantize(Decimal("0.1"))
            product.review_count = reviews.count()
        else:
            product.average_rating = Decimal("0.0")
            product.review_count = 0

        product.save(update_fields=["average_rating", "review_count"])

    def _update_product_rating_for_deletion(self, product):
        """Update product rating after review deletion."""
        from decimal import Decimal

        reviews = product.reviews.filter(is_approved=True)

        if reviews.exists():
            total = sum(r.rating for r in reviews)
            avg = Decimal(total) / Decimal(reviews.count())
            product.average_rating = avg.quantize(Decimal("0.1"))
            product.review_count = reviews.count()
        else:
            product.average_rating = Decimal("0.0")
            product.review_count = 0

        product.save(update_fields=["average_rating", "review_count"])

    def mark_helpful(self, user_id: int, helpful: bool = True) -> bool:
        """Mark review as helpful or not helpful."""
        from .review import ReviewVote

        vote, created = ReviewVote.objects.get_or_create(
            review=self,
            user_id=user_id,
            defaults={"is_helpful": helpful}
        )

        if not created:
            if vote.is_helpful == helpful:
                # Remove vote if same as before
                vote.delete()
            else:
                vote.is_helpful = helpful
                vote.save()

        # Update counts
        self.helpful_count = self.votes.filter(is_helpful=True).count()
        self.not_helpful_count = self.votes.filter(is_helpful=False).count()
        self.save(update_fields=["helpful_count", "not_helpful_count"])

        return True

    @property
    def can_edit(self) -> bool:
        """Check if review can still be edited (within 30 days)."""
        from django.utils import timezone
        from datetime import timedelta

        return self.created_at >= timezone.now() - timedelta(days=30)


class ReviewVote(models.Model):
    """
    User votes on review helpfulness.
    """

    class Meta:
        db_table = "review_votes"
        verbose_name = "Review Vote"
        verbose_name_plural = "Review Votes"
        unique_together = [["review", "user"]]

    review = models.ForeignKey(
        Review,
        on_delete=models.CASCADE,
        related_name="votes",
    )
    user = models.ForeignKey(
        "User",
        on_delete=models.CASCADE,
    )
    is_helpful = models.BooleanField(default=True)

    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"{self.user.email} - {'Helpful' if self.is_helpful else 'Not Helpful'}"


class ReviewImage(models.Model):
    """
    Images uploaded with product reviews.
    """

    class Meta:
        db_table = "review_images"
        verbose_name = "Review Image"
        verbose_name_plural = "Review Images"

    review = models.ForeignKey(
        Review,
        on_delete=models.CASCADE,
        related_name="images",
    )
    image = models.ImageField(upload_to="reviews/")
    caption = models.CharField(max_length=255, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"Image for review {self.review.id}"
