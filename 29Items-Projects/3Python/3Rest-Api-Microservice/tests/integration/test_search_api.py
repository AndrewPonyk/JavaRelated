"""
Integration Tests for Search and Analytics API Endpoints
"""
import pytest
import json


class TestSearchEndpoints:
    """Integration tests for /api/search endpoints."""

    def test_simple_search(self, client, sample_customers):
        """Test simple search endpoint."""
        response = client.get('/api/search/?q=John')

        assert response.status_code == 200
        data = response.get_json()
        assert data['query'] == 'John'
        assert len(data['data']) == 1

    def test_search_by_field(self, client, sample_customers):
        """Test search with field filter."""
        response = client.get('/api/search/?q=Acme&field=company')

        assert response.status_code == 200
        data = response.get_json()
        assert len(data['data']) == 2

    def test_search_pagination(self, client, sample_customers):
        """Test search pagination."""
        response = client.get('/api/search/?q=example&page=1&per_page=2')

        assert response.status_code == 200
        data = response.get_json()
        assert len(data['data']) == 2
        assert data['pagination']['per_page'] == 2

    def test_search_no_results(self, client, sample_customers):
        """Test search with no matching results."""
        response = client.get('/api/search/?q=NonExistentTerm')

        assert response.status_code == 200
        data = response.get_json()
        assert len(data['data']) == 0
        assert data['pagination']['total'] == 0

    def test_search_empty_query(self, client, sample_customers):
        """Test search with empty query returns all."""
        response = client.get('/api/search/?q=')

        assert response.status_code == 200
        data = response.get_json()
        assert data['pagination']['total'] == 4


class TestAdvancedSearchEndpoints:
    """Integration tests for /api/search/advanced endpoint."""

    def test_advanced_search(self, client, json_headers, sample_customers):
        """Test advanced search endpoint."""
        search_data = {
            'filters': {
                'status': 'active',
                'company': 'Acme'
            },
            'sort_by': 'name',
            'sort_order': 'asc'
        }

        response = client.post(
            '/api/search/advanced',
            data=json.dumps(search_data),
            headers=json_headers
        )

        assert response.status_code == 200
        data = response.get_json()
        assert len(data['data']) == 1
        assert data['data'][0]['company'] == 'Acme Inc'

    def test_advanced_search_empty_filters(self, client, json_headers, sample_customers):
        """Test advanced search with empty filters."""
        search_data = {
            'filters': {},
            'sort_by': 'created_at',
            'sort_order': 'desc'
        }

        response = client.post(
            '/api/search/advanced',
            data=json.dumps(search_data),
            headers=json_headers
        )

        assert response.status_code == 200
        data = response.get_json()
        assert data['pagination']['total'] == 4

    def test_advanced_search_status_filter(self, client, json_headers, sample_customers):
        """Test advanced search with status filter."""
        search_data = {
            'filters': {'status': 'inactive'}
        }

        response = client.post(
            '/api/search/advanced',
            data=json.dumps(search_data),
            headers=json_headers
        )

        assert response.status_code == 200
        data = response.get_json()
        assert len(data['data']) == 1
        assert data['data'][0]['status'] == 'inactive'


class TestAnalyticsEndpoints:
    """Integration tests for /api/analytics endpoints."""

    def test_get_summary(self, client, sample_customers):
        """Test analytics summary endpoint."""
        response = client.get('/api/analytics/summary')

        assert response.status_code == 200
        data = response.get_json()
        assert 'total_customers' in data['data']
        assert 'by_status' in data['data']
        assert data['data']['total_customers'] == 4

    def test_get_by_status(self, client, sample_customers):
        """Test count by status endpoint."""
        response = client.get('/api/analytics/by-status')

        assert response.status_code == 200
        data = response.get_json()
        assert 'active' in data['data']
        assert data['data']['active'] == 2

    def test_get_by_company(self, client, sample_customers):
        """Test count by company endpoint."""
        response = client.get('/api/analytics/by-company?limit=5')

        assert response.status_code == 200
        data = response.get_json()
        assert isinstance(data['data'], list)
        # Acme Inc should be first with 2 customers
        assert any(item['company'] == 'Acme Inc' for item in data['data'])

    def test_get_sentiment_stats(self, client, sample_customers):
        """Test sentiment stats endpoint."""
        response = client.get('/api/analytics/sentiment')

        assert response.status_code == 200
        data = response.get_json()
        # No sentiment scores in sample data
        assert 'analyzed_count' in data['data']

    def test_get_trends(self, client, sample_customers):
        """Test trends endpoint."""
        response = client.get('/api/analytics/trends?period=month&range=6')

        assert response.status_code == 200
        data = response.get_json()
        assert isinstance(data['data'], list)


class TestNLPEndpoints:
    """Integration tests for /api/nlp endpoints (without loading models)."""

    def test_nlp_health(self, client):
        """Test NLP health endpoint."""
        response = client.get('/api/nlp/health')

        assert response.status_code == 200
        data = response.get_json()
        assert 'status' in data['data']

    def test_sentiment_missing_text(self, client, json_headers):
        """Test sentiment endpoint with missing text."""
        response = client.post(
            '/api/nlp/sentiment',
            data=json.dumps({}),
            headers=json_headers
        )

        assert response.status_code == 400

    def test_sentiment_empty_text(self, client, json_headers):
        """Test sentiment endpoint with empty text."""
        response = client.post(
            '/api/nlp/sentiment',
            data=json.dumps({'text': ''}),
            headers=json_headers
        )

        assert response.status_code == 400

    def test_batch_sentiment_missing_texts(self, client, json_headers):
        """Test batch sentiment endpoint with missing texts."""
        response = client.post(
            '/api/nlp/sentiment/batch',
            data=json.dumps({}),
            headers=json_headers
        )

        assert response.status_code == 400

    def test_batch_sentiment_empty_array(self, client, json_headers):
        """Test batch sentiment endpoint with empty array."""
        response = client.post(
            '/api/nlp/sentiment/batch',
            data=json.dumps({'texts': []}),
            headers=json_headers
        )

        assert response.status_code == 400

    def test_classify_missing_fields(self, client, json_headers):
        """Test classify endpoint with missing fields."""
        response = client.post(
            '/api/nlp/classify',
            data=json.dumps({'text': 'test'}),
            headers=json_headers
        )

        assert response.status_code == 400
