"""
Search Tasks

Celery tasks for Elasticsearch synchronization.
"""

from celery import shared_task
import logging

logger = logging.getLogger("backend")


@shared_task(bind=True, autoretry_for=(Exception,), retry_backoff=True, retry_kwargs={"max_retries": 3})
def update_product_search_index(self, product_id: int) -> bool:
    """
    Update a product in Elasticsearch index.

    Args:
        product_id: The product ID

    Returns:
        True if indexed successfully
    """
    from backend.core.models import Product
    from backend.api.services.search_service import SearchService

    try:
        product = Product.objects.get(id=product_id)
        search_service = SearchService()

        product_data = {
            "id": product.id,
            "name": product.name,
            "slug": product.slug,
            "description": product.description,
            "short_description": product.short_description,
            "price": float(product.price),
            "category": product.category_id,
            "category_name": product.category.name if product.category else "",
            "vendor": product.vendor_id,
            "vendor_name": product.vendor.name if product.vendor else "",
            "stock": product.stock,
            "average_rating": float(product.average_rating),
            "review_count": product.review_count,
            "is_active": product.is_active,
            "created_at": product.created_at.isoformat(),
        }

        return search_service.index_product(product_data)

    except Exception as e:
        logger.error(f"Failed to index product {product_id}: {e}")
        raise


@shared_task
def bulk_update_search_index() -> dict:
    """
    Bulk update all products in Elasticsearch.

    Returns:
        Dictionary with update results
    """
    from backend.core.models import Product
    from backend.api.services.search_service import SearchService

    search_service = SearchService()
    products = Product.objects.all()

    success_count = 0
    error_count = 0

    for product in products:
        try:
            product_data = {
                "id": product.id,
                "name": product.name,
                "slug": product.slug,
                "description": product.description,
                "short_description": product.short_description,
                "price": float(product.price),
                "category": product.category_id,
                "category_name": product.category.name if product.category else "",
                "vendor": product.vendor_id,
                "vendor_name": product.vendor.name if product.vendor else "",
                "stock": product.stock,
                "average_rating": float(product.average_rating),
                "review_count": product.review_count,
                "is_active": product.is_active,
                "created_at": product.created_at.isoformat(),
            }

            if search_service.index_product(product_data):
                success_count += 1
            else:
                error_count += 1

        except Exception as e:
            logger.error(f"Failed to index product {product.id}: {e}")
            error_count += 1

    logger.info(f"Bulk index complete: {success_count} success, {error_count} errors")

    return {
        "success": success_count,
        "errors": error_count,
        "total": products.count(),
    }


@shared_task
def delete_from_search_index(product_id: int) -> bool:
    """
    Delete a product from Elasticsearch index.

    Args:
        product_id: The product ID

    Returns:
        True if deleted successfully
    """
    from backend.api.services.search_service import SearchService

    search_service = SearchService()
    return search_service.delete_product(product_id)
