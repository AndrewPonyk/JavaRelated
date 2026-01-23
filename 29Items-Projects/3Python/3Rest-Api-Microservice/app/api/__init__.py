"""
API Blueprint Registration
"""
from flask import Blueprint

api_bp = Blueprint('api', __name__)


@api_bp.route('/', methods=['GET'])
def index():
    """
    API Welcome Endpoint
    ---
    tags:
      - General
    responses:
      200:
        description: Welcome message
        schema:
          type: object
          properties:
            message:
              type: string
            version:
              type: string
    """
    return {
        'message': 'Welcome to Customer Management API',
        'version': '1.0.0',
        'endpoints': {
            'customers': '/api/customers',
            'search': '/api/search',
            'analytics': '/api/analytics',
            'nlp': '/api/nlp',
            'docs': '/apidocs'
        }
    }


@api_bp.route('/health', methods=['GET'])
def health_check():
    """
    Health Check Endpoint
    ---
    tags:
      - General
    responses:
      200:
        description: Service health status
    """
    return {'status': 'healthy'}
