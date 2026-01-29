"""Search indexing Celery tasks."""
from celery import shared_task


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def index_product(self, product_id: str):
    """Index a single product in Elasticsearch."""
    from apps.search.services import SearchService

    try:
        SearchService.index_product(product_id)
    except Exception as exc:
        self.retry(exc=exc, countdown=30)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def remove_product_from_index(self, product_id: str):
    """Remove a product from Elasticsearch index."""
    from apps.search.documents import ProductDocument

    try:
        # Delete from index
        ProductDocument.get(id=product_id).delete()
    except Exception:
        pass  # Already deleted or never indexed


@shared_task(bind=True, soft_time_limit=3600)
def reindex_all_products(self):
    """Reindex all products in Elasticsearch."""
    from apps.search.services import SearchService

    count = SearchService.reindex_all()
    return {'indexed_count': count}


@shared_task(bind=True, soft_time_limit=300)
def bulk_index_products(self, product_ids: list):
    """Bulk index multiple products."""
    from apps.products.models import Product
    from apps.search.documents import ProductDocument

    products = Product.objects.filter(
        id__in=product_ids,
        status=Product.Status.ACTIVE
    ).select_related('category', 'vendor')

    ProductDocument().update(products.iterator(), chunk_size=100)

    return {'indexed_count': len(product_ids)}


@shared_task(bind=True, soft_time_limit=600)
def sync_search_index(self):
    """Periodic task to sync search index with database."""
    from apps.products.models import Product
    from apps.search.documents import ProductDocument
    from django.utils import timezone
    from datetime import timedelta

    # Index products updated in last hour
    cutoff = timezone.now() - timedelta(hours=1)

    products = Product.objects.filter(
        updated_at__gte=cutoff,
        status=Product.Status.ACTIVE
    ).select_related('category', 'vendor')

    if products.exists():
        ProductDocument().update(products.iterator(), chunk_size=100)

    # Remove inactive products from index
    inactive_products = Product.objects.filter(
        updated_at__gte=cutoff
    ).exclude(status=Product.Status.ACTIVE).values_list('id', flat=True)

    for product_id in inactive_products:
        remove_product_from_index.delay(str(product_id))

    return {
        'indexed': products.count(),
        'removed': len(inactive_products)
    }
