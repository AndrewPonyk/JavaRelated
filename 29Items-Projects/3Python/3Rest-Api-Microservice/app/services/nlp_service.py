"""
NLP Service - Hugging Face Transformers Integration
"""
from typing import Dict, Any, List, Optional
import logging
from flask import current_app
from app.utils.exceptions import NotFoundError, ExternalServiceError

logger = logging.getLogger(__name__)

# Lazy-loaded model cache
_sentiment_model = None
_classifier_model = None


class NLPService:
    """
    Service class for NLP operations using Hugging Face transformers.

    Uses lazy loading for models to minimize memory usage and startup time.
    """

    def _get_sentiment_model(self):
        """
        Get or initialize the sentiment analysis model.

        Returns:
            Hugging Face pipeline for sentiment analysis
        """
        global _sentiment_model
        if _sentiment_model is None:
            try:
                from transformers import pipeline
                model_name = current_app.config.get(
                    'HF_MODEL_NAME',
                    'distilbert-base-uncased-finetuned-sst-2-english'
                )
                cache_dir = current_app.config.get('HF_CACHE_DIR', './models')

                logger.info(f'Loading sentiment model: {model_name}')
                _sentiment_model = pipeline(
                    'sentiment-analysis',
                    model=model_name,
                    cache_dir=cache_dir,
                    device=-1  # Force CPU
                )
                logger.info('Sentiment model loaded successfully')
            except Exception as e:
                logger.error(f'Failed to load sentiment model: {e}')
                raise ExternalServiceError(f'NLP service unavailable: {str(e)}')
        return _sentiment_model

    def _get_classifier_model(self):
        """
        Get or initialize the zero-shot classification model.

        Returns:
            Hugging Face pipeline for zero-shot classification
        """
        global _classifier_model
        if _classifier_model is None:
            try:
                from transformers import pipeline
                cache_dir = current_app.config.get('HF_CACHE_DIR', './models')

                logger.info('Loading zero-shot classifier')
                _classifier_model = pipeline(
                    'zero-shot-classification',
                    model='facebook/bart-large-mnli',
                    cache_dir=cache_dir,
                    device=-1
                )
                logger.info('Classifier model loaded successfully')
            except Exception as e:
                logger.error(f'Failed to load classifier model: {e}')
                raise ExternalServiceError(f'NLP classification service unavailable: {str(e)}')
        return _classifier_model

    def analyze_sentiment(self, text: str) -> Dict[str, Any]:
        """
        Analyze sentiment of a single text.

        Args:
            text: Text to analyze

        Returns:
            Sentiment result with label and score
        """
        model = self._get_sentiment_model()
        result = model(text[:512])[0]  # Truncate to model max length

        return {
            'label': result['label'],
            'score': round(result['score'], 4),
            'normalized_score': self._normalize_score(result)
        }

    def analyze_sentiment_batch(self, texts: List[str]) -> List[Dict[str, Any]]:
        """
        Analyze sentiment of multiple texts.

        Args:
            texts: List of texts to analyze

        Returns:
            List of sentiment results
        """
        model = self._get_sentiment_model()

        # Truncate texts to model max length
        truncated_texts = [t[:512] for t in texts]
        results = model(truncated_texts)

        return [
            {
                'text': texts[i][:100] + '...' if len(texts[i]) > 100 else texts[i],
                'label': r['label'],
                'score': round(r['score'], 4),
                'normalized_score': self._normalize_score(r)
            }
            for i, r in enumerate(results)
        ]

    def _normalize_score(self, result: Dict[str, Any]) -> float:
        """
        Normalize sentiment score to 0-1 range.

        POSITIVE with high confidence -> closer to 1
        NEGATIVE with high confidence -> closer to 0
        """
        score = result['score']
        if result['label'] == 'POSITIVE':
            return round(0.5 + (score * 0.5), 4)
        else:
            return round(0.5 - (score * 0.5), 4)

    def analyze_customer_sentiment(self, customer_id: int) -> Dict[str, Any]:
        """
        Analyze a customer's notes and update their sentiment score.

        Args:
            customer_id: Customer ID

        Returns:
            Analysis result with updated customer data
        """
        from app.services.customer_service import CustomerService

        customer_service = CustomerService()
        customer = customer_service.get_by_id(customer_id)

        if not customer:
            raise NotFoundError(f'Customer {customer_id} not found')

        if not customer.notes:
            return {
                'customer_id': customer_id,
                'analyzed': False,
                'message': 'No notes to analyze'
            }

        # Analyze sentiment
        result = self.analyze_sentiment(customer.notes)

        # Update customer
        customer_service.update_sentiment(customer_id, result['normalized_score'])

        return {
            'customer_id': customer_id,
            'analyzed': True,
            'sentiment': result,
            'updated_score': result['normalized_score']
        }

    def classify_text(
        self,
        text: str,
        candidate_labels: List[str]
    ) -> Dict[str, Any]:
        """
        Classify text into one of the candidate labels.

        Args:
            text: Text to classify
            candidate_labels: List of possible labels

        Returns:
            Classification result with scores
        """
        model = self._get_classifier_model()
        result = model(text[:512], candidate_labels)

        return {
            'text': text[:100] + '...' if len(text) > 100 else text,
            'labels': result['labels'],
            'scores': [round(s, 4) for s in result['scores']],
            'top_label': result['labels'][0],
            'top_score': round(result['scores'][0], 4)
        }

    def health_check(self) -> Dict[str, Any]:
        """
        Check NLP service health.

        Returns:
            Health status
        """
        status = {
            'sentiment_model': 'not_loaded',
            'classifier_model': 'not_loaded',
            'status': 'healthy'
        }

        global _sentiment_model, _classifier_model

        if _sentiment_model is not None:
            status['sentiment_model'] = 'loaded'

        if _classifier_model is not None:
            status['classifier_model'] = 'loaded'

        return status
