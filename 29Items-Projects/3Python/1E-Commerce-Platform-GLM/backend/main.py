"""
Django Application Entry Point

Main ASGI application configuration.
"""

import os
from django.core.asgi import get_asgi_application

# Set default Django settings module
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'backend.core.config.settings')

application = get_asgi_application()
