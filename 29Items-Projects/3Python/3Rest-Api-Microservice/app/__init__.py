"""
Flask Application Factory
"""
import logging
import sys
from flask import Flask, render_template, jsonify
from flask_cors import CORS
from app.config import config
from app.extensions import db, migrate, swagger


def create_app(config_name: str = 'development') -> Flask:
    """
    Application factory pattern for creating Flask app instances.

    Args:
        config_name: Configuration environment ('development', 'testing', 'production')

    Returns:
        Configured Flask application
    """
    app = Flask(__name__)
    app.config.from_object(config[config_name])

    # Configure logging
    _configure_logging(app)

    # Initialize extensions
    _init_extensions(app)

    # Configure CORS
    CORS(app, resources={
        r"/api/*": {
            "origins": app.config.get('CORS_ORIGINS', '*'),
            "methods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
            "allow_headers": ["Content-Type", "X-API-Key", "Authorization"]
        }
    })

    # Register blueprints
    _register_blueprints(app)

    # Register web routes
    _register_web_routes(app)

    # Register error handlers
    _register_error_handlers(app)

    app.logger.info(f'Application created with config: {config_name}')

    return app


def _configure_logging(app: Flask) -> None:
    """Configure application logging."""
    log_level = app.config.get('LOG_LEVEL', 'INFO')
    log_format = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'

    # Configure root logger
    logging.basicConfig(
        level=getattr(logging, log_level.upper(), logging.INFO),
        format=log_format,
        handlers=[logging.StreamHandler(sys.stdout)]
    )

    # Set Flask logger level
    app.logger.setLevel(getattr(logging, log_level.upper(), logging.INFO))

    # Reduce SQLAlchemy noise in non-debug mode
    if not app.debug:
        logging.getLogger('sqlalchemy.engine').setLevel(logging.WARNING)


def _init_extensions(app: Flask) -> None:
    """Initialize Flask extensions."""
    db.init_app(app)
    migrate.init_app(app, db)

    # Only init swagger if not testing
    if not app.config.get('TESTING'):
        swagger.init_app(app)


def _register_blueprints(app: Flask) -> None:
    """Register application blueprints."""
    from app.api import api_bp
    from app.api.customers import customers_bp
    from app.api.search import search_bp
    from app.api.analytics import analytics_bp
    from app.api.nlp import nlp_bp

    app.register_blueprint(api_bp, url_prefix='/api')
    app.register_blueprint(customers_bp, url_prefix='/api/customers')
    app.register_blueprint(search_bp, url_prefix='/api/search')
    app.register_blueprint(analytics_bp, url_prefix='/api/analytics')
    app.register_blueprint(nlp_bp, url_prefix='/api/nlp')


def _register_web_routes(app: Flask) -> None:
    """Register web UI routes."""

    @app.route('/')
    def index():
        """Render dashboard page."""
        return render_template('index.html')

    @app.route('/customers')
    def customers_page():
        """Render customers management page."""
        return render_template('index.html')


def _register_error_handlers(app: Flask) -> None:
    """Register global error handlers."""
    from app.utils.exceptions import AppException

    @app.errorhandler(AppException)
    def handle_app_exception(error):
        app.logger.warning(f'AppException: {error.message}')
        return jsonify(error.to_dict()), error.status_code

    @app.errorhandler(400)
    def handle_bad_request(error):
        return jsonify({'error': {'code': 'BAD_REQUEST', 'message': 'Bad request'}}), 400

    @app.errorhandler(404)
    def handle_not_found(error):
        return jsonify({'error': {'code': 'NOT_FOUND', 'message': 'Resource not found'}}), 404

    @app.errorhandler(405)
    def handle_method_not_allowed(error):
        return jsonify({'error': {'code': 'METHOD_NOT_ALLOWED', 'message': 'Method not allowed'}}), 405

    @app.errorhandler(500)
    def handle_internal_error(error):
        app.logger.error(f'Internal error: {error}')
        return jsonify({'error': {'code': 'INTERNAL_ERROR', 'message': 'An unexpected error occurred'}}), 500
