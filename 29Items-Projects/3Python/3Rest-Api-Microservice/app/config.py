"""
Application Configuration Classes

|su:11) CONFIGURATION PATTERN - Use classes for config with inheritance. Base class has shared
        settings, child classes override for specific environments (dev/test/prod).
"""
import os
from dotenv import load_dotenv

# |su:12) LOAD .ENV FILE - python-dotenv reads .env file into environment variables
load_dotenv()


class Config:
    """Base configuration."""
    # |su:13) SECRET_KEY - Used by Flask for session signing, CSRF tokens. MUST be secret in prod!
    SECRET_KEY = os.environ.get('SECRET_KEY', 'dev-secret-key-change-in-production')
    SQLALCHEMY_TRACK_MODIFICATIONS = False
    SQLALCHEMY_ECHO = False

    # CORS configuration
    CORS_ORIGINS = os.environ.get('CORS_ORIGINS', '*')

    # Swagger configuration
    SWAGGER = {
        'title': 'Customer Management API',
        'uiversion': 3,
        'version': '1.0.0',
        'description': 'REST API for customer management with NLP capabilities',
        'specs_route': '/apidocs/'
    }

    # NLP configuration
    HF_MODEL_NAME = os.environ.get('HF_MODEL_NAME', 'distilbert-base-uncased-finetuned-sst-2-english')
    HF_CACHE_DIR = os.environ.get('HF_CACHE_DIR', './models')

    # API configuration
    API_KEY = os.environ.get('API_KEY')
    RATE_LIMIT = os.environ.get('RATE_LIMIT', '100/hour')

    # Pagination
    DEFAULT_PAGE_SIZE = 20
    MAX_PAGE_SIZE = 100

    # Logging
    LOG_LEVEL = os.environ.get('LOG_LEVEL', 'INFO')


class DevelopmentConfig(Config):
    """Development configuration."""
    DEBUG = True
    LOG_LEVEL = 'DEBUG'
    SQLALCHEMY_DATABASE_URI = os.environ.get(
        'DATABASE_URL',
        'mysql://root:password@localhost:3306/customer_api_dev'
    )
    SQLALCHEMY_ECHO = False  # Set to True to see SQL queries


class TestingConfig(Config):
    """Testing configuration - uses SQLite for easy testing without MySQL."""
    TESTING = True
    DEBUG = True
    LOG_LEVEL = 'WARNING'
    # Use SQLite in-memory for tests (no MySQL required)
    SQLALCHEMY_DATABASE_URI = os.environ.get(
        'TEST_DATABASE_URL',
        'sqlite:///:memory:'
    )
    WTF_CSRF_ENABLED = False
    # Disable NLP model loading in tests by default
    HF_MODEL_NAME = None


class ProductionConfig(Config):
    """Production configuration."""
    DEBUG = False
    LOG_LEVEL = 'WARNING'
    SQLALCHEMY_DATABASE_URI = os.environ.get('DATABASE_URL')
    SQLALCHEMY_ENGINE_OPTIONS = {
        'pool_size': 10,
        'pool_recycle': 3600,
        'pool_pre_ping': True
    }


config = {
    'development': DevelopmentConfig,
    'testing': TestingConfig,
    'production': ProductionConfig,
    'default': DevelopmentConfig
}
