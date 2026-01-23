"""
API Blueprint Registration

|su:54) ROOT API BLUEPRINT - Base /api endpoint with welcome message and health check.
        Every API should have a health endpoint for monitoring/load balancers.
"""
from flask import Blueprint

api_bp = Blueprint('api', __name__)


# |su:55) API ROOT - Returns available endpoints, useful for API discovery
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


# |su:56) HEALTH CHECK - Essential for Docker/K8s health probes and load balancer checks
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
