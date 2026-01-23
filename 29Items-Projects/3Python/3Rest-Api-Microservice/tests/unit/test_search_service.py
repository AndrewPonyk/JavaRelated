"""
Unit Tests for Search Service
"""
import pytest
from app.services.search_service import SearchService
from app.models.customer import Customer
from app.extensions import db


class TestSimpleSearch:
    """Test cases for SearchService.search()."""

    def test_search_by_name(self, app, sample_customers):
        """Test searching by name."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='John', field='name')

            assert result['total'] == 1
            assert result['items'][0].name == 'John Doe'

    def test_search_by_email(self, app, sample_customers):
        """Test searching by email."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='jane@', field='email')

            assert result['total'] == 1
            assert 'jane' in result['items'][0].email

    def test_search_by_company(self, app, sample_customers):
        """Test searching by company."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='Acme', field='company')

            assert result['total'] == 2

    def test_search_all_fields(self, app, sample_customers):
        """Test searching across all fields."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='Tech', field='all')

            assert result['total'] == 1
            assert result['items'][0].company == 'Tech Corp'

    def test_search_no_results(self, app, sample_customers):
        """Test search with no matching results."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='NonExistent')

            assert result['total'] == 0
            assert len(result['items']) == 0

    def test_search_case_insensitive(self, app, sample_customers):
        """Test that search is case insensitive."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='JOHN')

            assert result['total'] == 1

    def test_search_empty_query(self, app, sample_customers):
        """Test search with empty query returns all."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='')

            assert result['total'] == 4

    def test_search_partial_match(self, app, sample_customers):
        """Test partial string matching."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='example.com')

            assert result['total'] == 4  # All have this domain

    def test_search_pagination(self, app, sample_customers):
        """Test search pagination."""
        with app.app_context():
            service = SearchService()
            result = service.search(query='example', page=1, per_page=2)

            assert len(result['items']) == 2
            assert result['page'] == 1
            assert result['per_page'] == 2
            assert result['pages'] == 2


class TestAdvancedSearch:
    """Test cases for SearchService.advanced_search()."""

    def test_advanced_search_with_status_filter(self, app, sample_customers):
        """Test advanced search with status filter."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(
                filters={'status': 'active'}
            )

            assert result['total'] == 2
            assert all(c.status == 'active' for c in result['items'])

    def test_advanced_search_with_multiple_filters(self, app, sample_customers):
        """Test advanced search with multiple filters."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(
                filters={
                    'company': 'Acme',
                    'status': 'active'
                }
            )

            assert result['total'] == 1
            assert result['items'][0].name == 'John Doe'

    def test_advanced_search_name_filter(self, app, sample_customers):
        """Test advanced search with name filter."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(
                filters={'name': 'Jane'}
            )

            assert result['total'] == 1
            assert result['items'][0].name == 'Jane Smith'

    def test_advanced_search_email_filter(self, app, sample_customers):
        """Test advanced search with email filter."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(
                filters={'email': 'bob@'}
            )

            assert result['total'] == 1
            assert 'bob' in result['items'][0].email

    def test_advanced_search_sorting_asc(self, app, sample_customers):
        """Test advanced search with ascending sort."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(
                filters={},
                sort_by='name',
                sort_order='asc'
            )

            names = [c.name for c in result['items']]
            assert names == sorted(names)

    def test_advanced_search_sorting_desc(self, app, sample_customers):
        """Test advanced search with descending sort."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(
                filters={},
                sort_by='name',
                sort_order='desc'
            )

            names = [c.name for c in result['items']]
            assert names == sorted(names, reverse=True)

    def test_advanced_search_pagination(self, app, sample_customers):
        """Test advanced search pagination."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(
                filters={},
                page=1,
                per_page=2
            )

            assert len(result['items']) == 2
            assert result['pages'] == 2

    def test_advanced_search_empty_filters(self, app, sample_customers):
        """Test advanced search with empty filters returns all."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(filters={})

            assert result['total'] == 4

    def test_advanced_search_no_results(self, app, sample_customers):
        """Test advanced search with filters that match nothing."""
        with app.app_context():
            service = SearchService()
            result = service.advanced_search(
                filters={'name': 'NonExistent'}
            )

            assert result['total'] == 0
