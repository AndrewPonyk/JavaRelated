"""Search service layer for E-Commerce Platform."""
from typing import Optional

from elasticsearch_dsl import Q, Search

from .documents import ProductDocument


class SearchService:
    """Service class for search-related business logic."""

    @staticmethod
    def search_products(
        query: str = '',
        category: Optional[str] = None,
        vendor: Optional[str] = None,
        min_price: Optional[float] = None,
        max_price: Optional[float] = None,
        sort: str = 'relevance',
        page: int = 1,
        page_size: int = 20
    ) -> dict:
        """Search products with filters and facets."""
        search = ProductDocument.search()

        # Build query
        if query:
            search = search.query(
                Q('multi_match',
                  query=query,
                  fields=['name^3', 'short_description^2', 'description', 'sku'],
                  fuzziness='AUTO')
            )

        # Apply filters
        if category:
            search = search.filter('term', category__slug=category)

        if vendor:
            search = search.filter('term', vendor__slug=vendor)

        if min_price is not None:
            search = search.filter('range', price={'gte': float(min_price)})

        if max_price is not None:
            search = search.filter('range', price={'lte': float(max_price)})

        # Only active products
        search = search.filter('term', status='active')

        # Sorting
        sort_mapping = {
            'relevance': '_score',
            'price_asc': 'price',
            'price_desc': '-price',
            'newest': '-created_at',
            'name_asc': 'name.keyword',
            'name_desc': '-name.keyword',
        }
        search = search.sort(sort_mapping.get(sort, '_score'))

        # Add aggregations for faceted search
        search.aggs.bucket('categories', 'terms', field='category.slug')
        search.aggs.bucket('vendors', 'terms', field='vendor.slug')
        search.aggs.bucket('price_ranges', 'range', field='price', ranges=[
            {'to': 25},
            {'from': 25, 'to': 50},
            {'from': 50, 'to': 100},
            {'from': 100, 'to': 250},
            {'from': 250},
        ])

        # Pagination
        start = (page - 1) * page_size
        search = search[start:start + page_size]

        # Execute search
        response = search.execute()

        # Format results
        products = []
        for hit in response:
            products.append({
                'id': str(hit.id),
                'sku': hit.sku,
                'name': hit.name,
                'slug': hit.slug,
                'short_description': hit.short_description,
                'price': float(hit.price),
                'compare_at_price': float(hit.compare_at_price) if hit.compare_at_price else None,
                'category': {
                    'name': hit.category.name,
                    'slug': hit.category.slug,
                } if hit.category else None,
                'vendor': {
                    'name': hit.vendor.business_name,
                    'slug': hit.vendor.slug,
                } if hit.vendor else None,
                'primary_image': hit.primary_image,
                'is_featured': hit.is_featured,
                'score': hit.meta.score,
            })

        # Format facets
        facets = {
            'categories': [
                {'slug': bucket.key, 'count': bucket.doc_count}
                for bucket in response.aggregations.categories.buckets
            ],
            'vendors': [
                {'slug': bucket.key, 'count': bucket.doc_count}
                for bucket in response.aggregations.vendors.buckets
            ],
            'price_ranges': [
                {'range': f"{bucket.get('from', 0)}-{bucket.get('to', '+')}", 'count': bucket.doc_count}
                for bucket in response.aggregations.price_ranges.buckets
            ],
        }

        return {
            'products': products,
            'total': response.hits.total.value,
            'page': page,
            'page_size': page_size,
            'total_pages': (response.hits.total.value + page_size - 1) // page_size,
            'facets': facets,
        }

    @staticmethod
    def get_suggestions(query: str, limit: int = 10) -> list:
        """Get search suggestions using prefix matching."""
        search = ProductDocument.search()

        search = search.query(
            Q('multi_match',
              query=query,
              fields=['name^3', 'short_description'],
              type='phrase_prefix')
        )

        search = search.filter('term', status='active')
        search = search[:limit]

        response = search.execute()

        suggestions = []
        seen = set()
        for hit in response:
            if hit.name.lower() not in seen:
                suggestions.append({
                    'text': hit.name,
                    'slug': hit.slug,
                    'category': hit.category.name if hit.category else None,
                })
                seen.add(hit.name.lower())

        return suggestions

    @staticmethod
    def index_product(product_id) -> None:
        """Index a single product."""
        from apps.products.models import Product
        try:
            product = Product.objects.select_related(
                'category', 'vendor'
            ).get(id=product_id)

            if product.status == Product.Status.ACTIVE:
                ProductDocument().update(product)
            else:
                # Remove from index if not active
                ProductDocument().delete(product, ignore=404)
        except Product.DoesNotExist:
            pass

    @staticmethod
    def reindex_all() -> int:
        """Reindex all products. Returns count of indexed products."""
        from apps.products.models import Product

        products = Product.objects.select_related(
            'category', 'vendor'
        ).filter(status=Product.Status.ACTIVE)

        # Use bulk indexing
        ProductDocument().update(products.iterator(), chunk_size=500)

        return products.count()
