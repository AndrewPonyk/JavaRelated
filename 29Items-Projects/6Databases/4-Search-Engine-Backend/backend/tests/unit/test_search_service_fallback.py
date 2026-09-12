"""LTR degradation policy: a failing rescore falls back to BM25, never to a 503."""

from unittest.mock import AsyncMock

import pytest
from elasticsearch import ApiError, TransportError

from app.core.config import Settings
from app.core.exceptions import SearchBackendUnavailableError
from app.search.query_builder import SearchQuery
from app.services.search_service import SearchService
from tests.conftest import ES_SEARCH_RESPONSE


def make_api_error() -> ApiError:
    class _Meta:
        status = 400

    return ApiError(message="sltr model missing", meta=_Meta(), body={})


@pytest.fixture
def ltr_settings() -> Settings:
    return Settings(ltr_mode="plugin", ltr_model_name="m1")


async def test_rescore_failure_retries_without_rescore(ltr_settings: Settings) -> None:
    es = AsyncMock()
    es.search.side_effect = [make_api_error(), ES_SEARCH_RESPONSE]
    service = SearchService(es=es, settings=ltr_settings)

    response = await service.search(SearchQuery(query="tv"))

    assert response.meta.total == 2  # served, degraded to BM25
    assert es.search.await_count == 2
    first_body = es.search.await_args_list[0].kwargs["body"]
    second_body = es.search.await_args_list[1].kwargs["body"]
    assert "rescore" in first_body
    assert "rescore" not in second_body


async def test_fallback_failure_maps_to_unavailable(ltr_settings: Settings) -> None:
    es = AsyncMock()
    es.search.side_effect = [make_api_error(), TransportError("es down")]
    service = SearchService(es=es, settings=ltr_settings)

    with pytest.raises(SearchBackendUnavailableError):
        await service.search(SearchQuery(query="tv"))


async def test_transport_error_without_ltr_is_unavailable() -> None:
    es = AsyncMock()
    es.search.side_effect = TransportError("es down")
    service = SearchService(es=es, settings=Settings())

    with pytest.raises(SearchBackendUnavailableError):
        await service.search(SearchQuery(query="tv"))
    assert es.search.await_count == 1  # no rescore in body -> no pointless retry
