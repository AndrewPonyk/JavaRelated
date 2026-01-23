"""
NLP API Endpoints using Hugging Face Transformers
"""
from flask import Blueprint, request, jsonify
from app.services.nlp_service import NLPService
from app.utils.exceptions import ValidationError

nlp_bp = Blueprint('nlp', __name__)
nlp_service = NLPService()


@nlp_bp.route('/sentiment', methods=['POST'])
def analyze_sentiment():
    """
    Analyze sentiment of text
    ---
    tags:
      - NLP
    parameters:
      - name: body
        in: body
        required: true
        schema:
          type: object
          required:
            - text
          properties:
            text:
              type: string
              description: Text to analyze
    responses:
      200:
        description: Sentiment analysis result
        schema:
          type: object
          properties:
            label:
              type: string
              enum: [POSITIVE, NEGATIVE]
            score:
              type: number
    """
    data = request.get_json()
    if not data or 'text' not in data:
        raise ValidationError('Text field is required')

    text = data['text']
    if not text or not text.strip():
        raise ValidationError('Text cannot be empty')

    result = nlp_service.analyze_sentiment(text)
    return jsonify({'data': result})


@nlp_bp.route('/sentiment/batch', methods=['POST'])
def analyze_sentiment_batch():
    """
    Analyze sentiment of multiple texts
    ---
    tags:
      - NLP
    parameters:
      - name: body
        in: body
        required: true
        schema:
          type: object
          required:
            - texts
          properties:
            texts:
              type: array
              items:
                type: string
    responses:
      200:
        description: Batch sentiment analysis results
    """
    data = request.get_json()
    if not data or 'texts' not in data:
        raise ValidationError('Texts array is required')

    texts = data['texts']
    if not isinstance(texts, list) or len(texts) == 0:
        raise ValidationError('Texts must be a non-empty array')

    if len(texts) > 100:
        raise ValidationError('Maximum 100 texts per batch')

    results = nlp_service.analyze_sentiment_batch(texts)
    return jsonify({'data': results})


@nlp_bp.route('/customer/<int:customer_id>/analyze', methods=['POST'])
def analyze_customer(customer_id: int):
    """
    Analyze customer notes and update sentiment score
    ---
    tags:
      - NLP
    parameters:
      - name: customer_id
        in: path
        type: integer
        required: true
    responses:
      200:
        description: Customer sentiment updated
      404:
        description: Customer not found
    """
    result = nlp_service.analyze_customer_sentiment(customer_id)
    return jsonify({'data': result})


@nlp_bp.route('/classify', methods=['POST'])
def classify_text():
    """
    Classify text into predefined categories
    ---
    tags:
      - NLP
    parameters:
      - name: body
        in: body
        required: true
        schema:
          type: object
          required:
            - text
            - labels
          properties:
            text:
              type: string
            labels:
              type: array
              items:
                type: string
    responses:
      200:
        description: Classification result
    """
    data = request.get_json()
    if not data or 'text' not in data or 'labels' not in data:
        raise ValidationError('Text and labels are required')

    result = nlp_service.classify_text(
        text=data['text'],
        candidate_labels=data['labels']
    )
    return jsonify({'data': result})


@nlp_bp.route('/health', methods=['GET'])
def nlp_health():
    """
    Check NLP service health
    ---
    tags:
      - NLP
    responses:
      200:
        description: NLP service status
    """
    status = nlp_service.health_check()
    return jsonify({'data': status})
