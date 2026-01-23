"""
Unit Tests for NLP Service

These tests use mocking to avoid loading actual ML models.
"""
import pytest
from unittest.mock import patch, MagicMock
import sys


class TestSentimentAnalysis:
    """Test cases for sentiment analysis functionality."""

    def test_analyze_sentiment_positive(self, app):
        """Test sentiment analysis returns positive result."""
        # Reset the global model cache
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None

        with app.app_context():
            # Create a mock model that will be used when _get_sentiment_model is called
            mock_model = MagicMock()
            mock_model.return_value = [{'label': 'POSITIVE', 'score': 0.95}]

            # Directly set the cached model to bypass the loading
            nlp_module._sentiment_model = mock_model

            from app.services.nlp_service import NLPService
            service = NLPService()

            result = service.analyze_sentiment('This is great!')

            assert result['label'] == 'POSITIVE'
            assert result['score'] == 0.95
            assert result['normalized_score'] > 0.5

    def test_analyze_sentiment_negative(self, app):
        """Test sentiment analysis returns negative result."""
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None

        with app.app_context():
            mock_model = MagicMock()
            mock_model.return_value = [{'label': 'NEGATIVE', 'score': 0.85}]
            nlp_module._sentiment_model = mock_model

            from app.services.nlp_service import NLPService
            service = NLPService()

            result = service.analyze_sentiment('This is terrible!')

            assert result['label'] == 'NEGATIVE'
            assert result['normalized_score'] < 0.5

    def test_analyze_sentiment_batch(self, app):
        """Test batch sentiment analysis."""
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None

        with app.app_context():
            mock_model = MagicMock()
            mock_model.return_value = [
                {'label': 'POSITIVE', 'score': 0.9},
                {'label': 'NEGATIVE', 'score': 0.8}
            ]
            nlp_module._sentiment_model = mock_model

            from app.services.nlp_service import NLPService
            service = NLPService()

            texts = ['Good product', 'Bad service']
            results = service.analyze_sentiment_batch(texts)

            assert len(results) == 2
            assert results[0]['label'] == 'POSITIVE'
            assert results[1]['label'] == 'NEGATIVE'


class TestTextClassification:
    """Test cases for text classification functionality."""

    def test_classify_text(self, app):
        """Test text classification."""
        import app.services.nlp_service as nlp_module
        nlp_module._classifier_model = None

        with app.app_context():
            mock_model = MagicMock()
            mock_model.return_value = {
                'labels': ['support', 'sales', 'billing'],
                'scores': [0.8, 0.15, 0.05]
            }
            nlp_module._classifier_model = mock_model

            from app.services.nlp_service import NLPService
            service = NLPService()

            result = service.classify_text(
                'I need help with my account',
                ['support', 'sales', 'billing']
            )

            assert result['top_label'] == 'support'
            assert result['top_score'] == 0.8


class TestHealthCheck:
    """Test cases for NLP service health check."""

    def test_health_check_models_not_loaded(self, app):
        """Test health check when models are not loaded."""
        # Reset global model caches
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None
        nlp_module._classifier_model = None

        with app.app_context():
            from app.services.nlp_service import NLPService
            service = NLPService()

            status = service.health_check()

            assert status['sentiment_model'] == 'not_loaded'
            assert status['classifier_model'] == 'not_loaded'
            assert status['status'] == 'healthy'

    def test_health_check_models_loaded(self, app):
        """Test health check when models are loaded."""
        import app.services.nlp_service as nlp_module

        with app.app_context():
            # Set mock models as loaded
            nlp_module._sentiment_model = MagicMock()
            nlp_module._classifier_model = MagicMock()

            from app.services.nlp_service import NLPService
            service = NLPService()

            status = service.health_check()

            assert status['sentiment_model'] == 'loaded'
            assert status['classifier_model'] == 'loaded'
            assert status['status'] == 'healthy'


class TestScoreNormalization:
    """Test sentiment score normalization."""

    @pytest.mark.parametrize("label,score,expected_min,expected_max", [
        ('POSITIVE', 1.0, 0.99, 1.0),
        ('POSITIVE', 0.5, 0.74, 0.76),
        ('NEGATIVE', 1.0, 0.0, 0.01),
        ('NEGATIVE', 0.5, 0.24, 0.26),
    ])
    def test_score_normalization(self, app, label, score, expected_min, expected_max):
        """Test that scores are normalized to 0-1 range correctly."""
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None

        with app.app_context():
            mock_model = MagicMock()
            mock_model.return_value = [{'label': label, 'score': score}]
            nlp_module._sentiment_model = mock_model

            from app.services.nlp_service import NLPService
            service = NLPService()

            result = service.analyze_sentiment('test')

            assert expected_min <= result['normalized_score'] <= expected_max


class TestTextTruncation:
    """Test that long texts are truncated before analysis."""

    def test_short_text_not_truncated(self, app):
        """Test that short text is not truncated."""
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None

        with app.app_context():
            mock_model = MagicMock()
            mock_model.return_value = [{'label': 'POSITIVE', 'score': 0.5}]
            nlp_module._sentiment_model = mock_model

            from app.services.nlp_service import NLPService
            service = NLPService()

            short_text = "Short text"
            service.analyze_sentiment(short_text)

            # Check that the model was called with the original text
            call_args = mock_model.call_args[0][0]
            assert call_args == short_text

    def test_long_text_truncated(self, app):
        """Test that long text is truncated to 512 characters."""
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None

        with app.app_context():
            mock_model = MagicMock()
            mock_model.return_value = [{'label': 'POSITIVE', 'score': 0.5}]
            nlp_module._sentiment_model = mock_model

            from app.services.nlp_service import NLPService
            service = NLPService()

            # Create text longer than 512 characters
            long_text = "x" * 600
            service.analyze_sentiment(long_text)

            # Check that the model was called with truncated text
            call_args = mock_model.call_args[0][0]
            assert len(call_args) == 512


class TestCustomerSentimentAnalysis:
    """Test analyzing customer sentiment."""

    def test_analyze_customer_no_notes(self, app, sample_customer):
        """Test analyzing customer with no notes returns appropriate message."""
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None

        with app.app_context():
            from app.services.nlp_service import NLPService
            from app.services.customer_service import CustomerService
            from app.models.customer import Customer
            from app.extensions import db

            # Create customer without notes
            customer = Customer(
                name='No Notes Customer',
                email='nonotes@example.com',
                status='active',
                notes=None
            )
            db.session.add(customer)
            db.session.commit()

            service = NLPService()
            result = service.analyze_customer_sentiment(customer.id)

            assert result['analyzed'] is False
            assert 'No notes' in result['message']

    def test_analyze_customer_with_notes(self, app):
        """Test analyzing customer with notes updates sentiment."""
        import app.services.nlp_service as nlp_module
        nlp_module._sentiment_model = None

        with app.app_context():
            from app.services.nlp_service import NLPService
            from app.models.customer import Customer
            from app.extensions import db

            # Set up mock model
            mock_model = MagicMock()
            mock_model.return_value = [{'label': 'POSITIVE', 'score': 0.9}]
            nlp_module._sentiment_model = mock_model

            # Create customer with notes
            customer = Customer(
                name='Has Notes Customer',
                email='hasnotes@example.com',
                status='active',
                notes='This is a great customer with excellent feedback!'
            )
            db.session.add(customer)
            db.session.commit()

            service = NLPService()
            result = service.analyze_customer_sentiment(customer.id)

            assert result['analyzed'] is True
            assert result['sentiment']['label'] == 'POSITIVE'
            assert result['updated_score'] > 0.5

    def test_analyze_customer_not_found(self, app):
        """Test analyzing non-existent customer raises error."""
        import app.services.nlp_service as nlp_module

        with app.app_context():
            from app.services.nlp_service import NLPService
            from app.utils.exceptions import NotFoundError

            service = NLPService()

            with pytest.raises(NotFoundError):
                service.analyze_customer_sentiment(99999)
