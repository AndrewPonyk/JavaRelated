"""
Django WSGI Configuration

WSGI application for production deployment.
"""

import os
from django.core.wsgi import get_wsgi_application

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'backend.core.config.settings')

application = get_wsgi_application()
