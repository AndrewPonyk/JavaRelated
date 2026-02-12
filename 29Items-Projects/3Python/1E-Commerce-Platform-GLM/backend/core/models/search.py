"""
Search Models

Models for search analytics and tracking.
"""

from django.db import models
from django.contrib.postgres.search import TrigramSimilarity


class SearchQuery(models.Model):
    """
    Track search queries for analytics.
    """

    class Meta:
        db_table = "search_queries"
        verbose_name = "Search Query"
        verbose_name_plural = "Search Queries"
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["query"]),
            models.Index(fields=["user"]),
            models.Index(fields=["-created_at"]),
        ]

    user = models.ForeignKey(
        "User",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="searches",
    )
    query = models.CharField(max_length=500, db_index=True)
    results_count = models.PositiveIntegerField(default=0)

    # Track filters used
    filters = models.JSONField(default=dict, blank=True)

    # Track if user clicked on any result
    clicked_result = models.ForeignKey(
        "Product",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="+",
    )

    # Session tracking for anonymous users
    session_key = models.CharField(max_length=255, blank=True, null=True)

    # Performance tracking
    response_time_ms = models.PositiveIntegerField(null=True, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"{self.query} ({self.results_count} results)"


class SearchAutocomplete(models.Model):
    """
    Popular search terms for autocomplete suggestions.
    """

    class Meta:
        db_table = "search_autocomplete"
        verbose_name = "Search Autocomplete"
        verbose_name_plural = "Search Autocompletes"
        ordering = ["-popularity"]

    term = models.CharField(max_length=255, unique=True, db_index=True)
    popularity = models.PositiveIntegerField(default=0)

    # Category context for better suggestions
    category = models.ForeignKey(
        "Category",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="autocomplete_terms",
    )

    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"{self.term} ({self.popularity})"

    @classmethod
    def record_search(cls, term: str, category_id: int = None) -> None:
        """Record a search term for autocomplete."""
        term_lower = term.lower().strip()

        autocomplete, created = cls.objects.get_or_create(
            term=term_lower,
            defaults={"category_id": category_id}
        )

        if not created:
            autocomplete.popularity += 1
            autocomplete.save(update_fields=["popularity", "updated_at"])

    @classmethod
    def get_suggestions(cls, query: str, limit: int = 10) -> list:
        """Get autocomplete suggestions for a query."""
        from django.contrib.postgres.search import TrigramSimilarity

        query_lower = query.lower().strip()

        # Use trigram similarity for fuzzy matching
        suggestions = cls.objects.filter(
            term__trigram_similar=query_lower)
        ).annotate(
            similarity=TrigramSimilarity("term", query_lower)
        ).filter(
            similarity__gt=0.3
        ).order_by("-similarity", "-popularity")[:limit]

        return [s.term for s in suggestions]
