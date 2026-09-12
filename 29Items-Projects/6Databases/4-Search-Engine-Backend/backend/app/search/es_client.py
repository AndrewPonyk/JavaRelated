"""AsyncElasticsearch client factory. Created once per process in the app lifespan."""

from elasticsearch import AsyncElasticsearch

from app.core.config import Settings


def create_es_client(settings: Settings) -> AsyncElasticsearch:
    return AsyncElasticsearch(
        settings.es_url,
        api_key=settings.es_api_key,
        request_timeout=settings.es_request_timeout_s,
        retry_on_timeout=True,
        max_retries=2,  # connection-level retries only; queries themselves are idempotent
    )
