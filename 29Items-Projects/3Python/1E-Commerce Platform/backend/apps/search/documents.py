"""Elasticsearch document definitions for E-Commerce Platform."""
from django_elasticsearch_dsl import Document, fields
from django_elasticsearch_dsl.registries import registry

from apps.products.models import Category, Product


@registry.register_document
class ProductDocument(Document):
    """Elasticsearch document for Product model."""

    # Nested fields
    category = fields.ObjectField(properties={
        'id': fields.IntegerField(),
        'name': fields.TextField(),
        'slug': fields.KeywordField(),
    })

    vendor = fields.ObjectField(properties={
        'id': fields.IntegerField(),
        'business_name': fields.TextField(),
        'slug': fields.KeywordField(),
    })

    # Computed fields
    primary_image = fields.TextField(attr='get_primary_image_url')

    class Index:
        name = 'products'
        settings = {
            'number_of_shards': 1,
            'number_of_replicas': 0,
            'analysis': {
                'analyzer': {
                    'autocomplete': {
                        'type': 'custom',
                        'tokenizer': 'standard',
                        'filter': ['lowercase', 'autocomplete_filter']
                    }
                },
                'filter': {
                    'autocomplete_filter': {
                        'type': 'edge_ngram',
                        'min_gram': 2,
                        'max_gram': 20
                    }
                }
            }
        }

    class Django:
        model = Product
        fields = [
            'id',
            'sku',
            'name',
            'slug',
            'short_description',
            'description',
            'price',
            'compare_at_price',
            'status',
            'is_featured',
            'created_at',
            'updated_at',
        ]
        # Ignore auto-update for performance (use Celery tasks)
        ignore_signals = True

    def get_queryset(self):
        """Return queryset optimized for indexing."""
        return super().get_queryset().select_related(
            'category', 'vendor'
        ).filter(status=Product.Status.ACTIVE)

    def get_primary_image_url(self):
        """Get primary image URL for the product."""
        primary = self.images.filter(is_primary=True).first()
        if primary:
            return primary.image.url
        first = self.images.first()
        return first.image.url if first else None


@registry.register_document
class CategoryDocument(Document):
    """Elasticsearch document for Category model."""

    class Index:
        name = 'categories'
        settings = {
            'number_of_shards': 1,
            'number_of_replicas': 0,
        }

    class Django:
        model = Category
        fields = [
            'id',
            'name',
            'slug',
            'description',
            'is_active',
        ]
        ignore_signals = True
