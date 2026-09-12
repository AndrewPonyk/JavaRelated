"""
Base Django settings shared by all environments.

Environment-specific modules (development/staging/production) import from here
and override as needed. Secrets and connection strings are read from the
environment — never hard-coded.
"""

from __future__ import annotations

from datetime import timedelta
from pathlib import Path

import environ

# backend/ directory (two levels up from this file).
BASE_DIR = Path(__file__).resolve().parent.parent.parent

env = environ.Env(
    DJANGO_DEBUG=(bool, False),
)

# --- Core ------------------------------------------------------------------
SECRET_KEY = env("DJANGO_SECRET_KEY", default="insecure-change-me-in-env")
DEBUG = env("DJANGO_DEBUG")
ALLOWED_HOSTS = env.list("DJANGO_ALLOWED_HOSTS", default=["localhost", "127.0.0.1"])

# --- Applications ----------------------------------------------------------
DJANGO_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
]

THIRD_PARTY_APPS = [
    "rest_framework",
    "rest_framework_simplejwt.token_blacklist",
    "corsheaders",
    "django_filters",
    "drf_spectacular",
]

LOCAL_APPS = [
    "apps.accounts",
    "apps.catalog",
    "apps.cart",
    "apps.orders",
    "apps.inventory",
    "apps.vendors",
    "apps.search",
    "apps.recommendations",
]

INSTALLED_APPS = DJANGO_APPS + THIRD_PARTY_APPS + LOCAL_APPS

# --- Middleware ------------------------------------------------------------
MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    # Compress responses (gzip) for bandwidth — safe for a JSON API.
    "django.middleware.gzip.GZipMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
    # Custom: attach a request_id and emit structured access logs.
    "common.middleware.request_id.RequestIDMiddleware",
]

ROOT_URLCONF = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

# --- Database --------------------------------------------------------------
# Reads DATABASE_URL, e.g. postgres://user:pass@host:5432/dbname
DATABASES = {
    "default": env.db(
        "DATABASE_URL", default="postgres://postgres:postgres@localhost:5432/ecommerce"
    ),
}
DATABASES["default"]["CONN_MAX_AGE"] = env.int("DB_CONN_MAX_AGE", default=60)

# Read replica (used by SQLAlchemy reporting layer; see common/utils/reporting.py).
READ_REPLICA_URL = env("READ_REPLICA_URL", default=env("DATABASE_URL", default=""))

# --- Auth ------------------------------------------------------------------
AUTH_USER_MODEL = "accounts.User"

# Argon2 is the preferred hasher (OWASP-recommended); PBKDF2 kept for
# verifying any legacy hashes.
PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.Argon2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2SHA1PasswordHasher",
    "django.contrib.auth.hashers.BCryptSHA256PasswordHasher",
]

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

# --- DRF / JWT -------------------------------------------------------------
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
    "DEFAULT_PERMISSION_CLASSES": ("rest_framework.permissions.IsAuthenticated",),
    "DEFAULT_FILTER_BACKENDS": (
        "django_filters.rest_framework.DjangoFilterBackend",
        "rest_framework.filters.SearchFilter",
        "rest_framework.filters.OrderingFilter",
    ),
    "DEFAULT_PAGINATION_CLASS": "common.utils.pagination.StandardResultsSetPagination",
    "PAGE_SIZE": 24,
    "EXCEPTION_HANDLER": "common.utils.exceptions.standard_exception_handler",
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    # API throttling / rate limiting.
    "DEFAULT_THROTTLE_CLASSES": (
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
    ),
    "DEFAULT_THROTTLE_RATES": {
        "anon": env("THROTTLE_ANON", default="60/min"),
        "user": env("THROTTLE_USER", default="1000/min"),
        "auth": env("THROTTLE_AUTH", default="10/min"),  # brute-force guard
    },
}

SPECTACULAR_SETTINGS = {
    "TITLE": "E-Commerce Platform API",
    "DESCRIPTION": "Catalog, cart, checkout, inventory, vendors, search, recommendations.",
    "VERSION": "1.0.0",
    "SERVE_INCLUDE_SCHEMA": False,
}

SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=15),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=7),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
}

# --- Caching & Celery ------------------------------------------------------
REDIS_URL = env("REDIS_URL", default="redis://localhost:6379/0")
CACHES = {
    "default": {
        "BACKEND": "django.core.cache.backends.redis.RedisCache",
        "LOCATION": env("CACHE_URL", default="redis://localhost:6379/1"),
    }
}

CELERY_BROKER_URL = env("CELERY_BROKER_URL", default="redis://localhost:6379/2")
CELERY_RESULT_BACKEND = env("CELERY_RESULT_BACKEND", default="redis://localhost:6379/3")
CELERY_TASK_ACKS_LATE = True
CELERY_TASK_REJECT_ON_WORKER_LOST = True
CELERY_TASK_DEFAULT_QUEUE = "default"

# --- Elasticsearch ---------------------------------------------------------
ELASTICSEARCH_URL = env("ELASTICSEARCH_URL", default="http://localhost:9200")
ELASTICSEARCH_PRODUCT_INDEX = env("ELASTICSEARCH_PRODUCT_INDEX", default="products")
# Toggle the catalog->ES indexing signals (disabled in tests).
SEARCH_INDEXING_ENABLED = env.bool("SEARCH_INDEXING_ENABLED", default=True)

# --- Storage (S3) ----------------------------------------------------------
AWS_STORAGE_BUCKET_NAME = env("AWS_STORAGE_BUCKET_NAME", default="")
AWS_S3_REGION_NAME = env("AWS_REGION", default="eu-central-1")
ML_MODEL_S3_PREFIX = env("ML_MODEL_S3_PREFIX", default="models/recommendations/")
ML_MODEL_LOCAL_PATH = env("ML_MODEL_LOCAL_PATH", default=str(BASE_DIR / "var" / "reco_model.pt"))

# --- Email -----------------------------------------------------------------
# Console backend in dev; SES/SMTP in staging+prod (overridden there).
EMAIL_BACKEND = env("EMAIL_BACKEND", default="django.core.mail.backends.console.EmailBackend")
DEFAULT_FROM_EMAIL = env("DEFAULT_FROM_EMAIL", default="orders@ecommerce.local")

# --- Payments --------------------------------------------------------------
# Selects the payment gateway implementation (apps.orders.payments).
# "fake" is deterministic + idempotent for local/test; "stripe" hits the API.
PAYMENT_GATEWAY = env("PAYMENT_GATEWAY", default="fake")
STRIPE_SECRET_KEY = env("STRIPE_SECRET_KEY", default="")
STRIPE_WEBHOOK_SECRET = env("STRIPE_WEBHOOK_SECRET", default="")

# --- I18N / static ---------------------------------------------------------
LANGUAGE_CODE = "en-us"
TIME_ZONE = "UTC"
USE_I18N = True
USE_TZ = True
STATIC_URL = "static/"
STATIC_ROOT = BASE_DIR / "staticfiles"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# --- Logging (structured JSON to stdout; see TECH-NOTES 2.6) ---------------
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "json": {
            "()": "common.utils.logging.JSONFormatter",
        },
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "json",
        },
    },
    "root": {"handlers": ["console"], "level": env("LOG_LEVEL", default="INFO")},
}
