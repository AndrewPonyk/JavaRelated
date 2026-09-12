"""Base settings — 12-factor, environment-driven.

Shared by all environments. Environment-specific modules (development.py,
staging.py, production.py) import * from here and override. Required secrets
fail fast in non-dev environments (see production.py).
"""
from __future__ import annotations

from datetime import timedelta
from pathlib import Path

import environ  # django-environ

BASE_DIR = Path(__file__).resolve().parent.parent.parent  # .../backend

env = environ.Env(
    DJANGO_DEBUG=(bool, False),
    DJANGO_ALLOWED_HOSTS=(list, []),
    DB_CONN_MAX_AGE=(int, 60),
    DB_SSL_REQUIRE=(bool, False),
    JWT_ACCESS_TTL_MINUTES=(int, 15),
    JWT_REFRESH_TTL_DAYS=(int, 7),
    ELIGIBILITY_MIN_CONFIDENCE=(float, 0.70),
    LOG_LEVEL=(str, "INFO"),
    CORS_ALLOWED_ORIGINS=(list, ["http://localhost:5173"]),
)

# Load .env in local/dev only; prod resolves secrets from Secrets Manager/SSM.
_env_file = BASE_DIR / ".env"
if _env_file.exists():
    environ.Env.read_env(str(_env_file))

# ── Core ──────────────────────────────────────────────────────────────────
# Dev/test default is ≥32 bytes so JWT HMAC signing (HS256) is valid; production
# requires a real secret via env (see production.py — fails fast if unset).
SECRET_KEY = env("DJANGO_SECRET_KEY", default="insecure-dev-only-key-do-not-use-in-production")
DEBUG = env("DJANGO_DEBUG")
ALLOWED_HOSTS = env("DJANGO_ALLOWED_HOSTS")

# ── Applications ──────────────────────────────────────────────────────────
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
    "rest_framework_simplejwt",
    "rest_framework_simplejwt.token_blacklist",  # refresh rotation + reuse detection
    "drf_spectacular",
    "corsheaders",
]
LOCAL_APPS = [
    "apps.accounts",
    "apps.audit",
    "apps.trials",
    "apps.sites",
    "apps.patients",
    "apps.enrollment",
    "apps.ecrf",
    "apps.eligibility",
    "apps.notifications",
    "apps.common",
]
INSTALLED_APPS = DJANGO_APPS + THIRD_PARTY_APPS + LOCAL_APPS

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    # Response compression. CloudFront/ALB also compress at the edge; this covers
    # direct hits. (JSON API, JWT-in-header — low BREACH surface.)
    "django.middleware.gzip.GZipMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
    # Captures actor + correlation id for the 21 CFR Part 11 audit trail.
    "apps.audit.middleware.AuditContextMiddleware",
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

# ── Database (PostgreSQL) ─────────────────────────────────────────────────
DATABASES = {
    "default": {
        **env.db("DATABASE_URL", default="postgres://ctms:ctms@localhost:5432/ctms"),
        "CONN_MAX_AGE": env("DB_CONN_MAX_AGE"),
        "OPTIONS": {"sslmode": "require"} if env("DB_SSL_REQUIRE") else {},
    }
}
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# Custom user (RBAC, MFA, e-signature creds).
AUTH_USER_MODEL = "accounts.User"

# ── Password validation ───────────────────────────────────────────────────
AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {
        "NAME": "django.contrib.auth.password_validation.MinimumLengthValidator",
        "OPTIONS": {"min_length": 12},
    },
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

# ── DRF / API ─────────────────────────────────────────────────────────────
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
        "rest_framework.authentication.SessionAuthentication",  # admin / browsable API
    ),
    "DEFAULT_PERMISSION_CLASSES": ("rest_framework.permissions.IsAuthenticated",),
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    "DEFAULT_THROTTLE_CLASSES": (
        "rest_framework.throttling.UserRateThrottle",
        "rest_framework.throttling.AnonRateThrottle",
    ),
    "DEFAULT_THROTTLE_RATES": {"user": "1000/hour", "anon": "20/hour"},
    "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.LimitOffsetPagination",
    "PAGE_SIZE": 50,
    # Consistent, PHI-free error envelope (ARCHITECTURE §2.6).
    "EXCEPTION_HANDLER": "apps.common.exceptions.api_exception_handler",
}

# Federated identity (OIDC/SAML to the enterprise IdP) terminates at the edge;
# the application issues short-lived JWTs with rotation + reuse detection.
SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=env("JWT_ACCESS_TTL_MINUTES")),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=env("JWT_REFRESH_TTL_DAYS")),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
    "UPDATE_LAST_LOGIN": True,
}

SPECTACULAR_SETTINGS = {
    "TITLE": "Clinical Trial Management API",
    "DESCRIPTION": "Protocol management, enrollment, eCRF and ML eligibility screening.",
    "VERSION": "1.0.0",
    "SERVE_INCLUDE_SCHEMA": False,
    # The Decision enum backs both the ML recommendation and the human decision.
    "ENUM_NAME_OVERRIDES": {"DecisionEnum": "apps.eligibility.models.Decision"},
}

# ── CORS ──────────────────────────────────────────────────────────────────
CORS_ALLOWED_ORIGINS = env("CORS_ALLOWED_ORIGINS")

# ── Celery ────────────────────────────────────────────────────────────────
CELERY_BROKER_URL = env("CELERY_BROKER_URL", default="redis://localhost:6379/0")
CELERY_RESULT_BACKEND = env("CELERY_RESULT_BACKEND", default="redis://localhost:6379/1")
# Tasks are fire-and-forget: progress is tracked via DB state (e.g. Screening.status),
# never by awaiting a Celery result. Ignoring results means enqueuing does not touch
# the result backend (faster, and decoupled from result-store availability).
CELERY_TASK_IGNORE_RESULT = True
CELERY_TASK_TRACK_STARTED = True
CELERY_TASK_TIME_LIMIT = 30 * 60
CELERY_TASK_SOFT_TIME_LIMIT = 25 * 60

# ── Domain / ML config ────────────────────────────────────────────────────
ML_BACKEND = env("ML_BACKEND", default="local")  # local | sagemaker
SAGEMAKER_ENDPOINT_NAME = env("SAGEMAKER_ENDPOINT_NAME", default="")
ELIGIBILITY_MIN_CONFIDENCE = env("ELIGIBILITY_MIN_CONFIDENCE")
# Field-level PHI encryption key (Fernet, base64 32-byte). Dev default is
# deterministic; production.py requires a real key from Secrets Manager.
FIELD_ENCRYPTION_KEY = env("FIELD_ENCRYPTION_KEY", default="")

# ── i18n / tz ─────────────────────────────────────────────────────────────
LANGUAGE_CODE = "en-us"
TIME_ZONE = "UTC"
USE_I18N = True
USE_TZ = True

# ── Static ────────────────────────────────────────────────────────────────
STATIC_URL = "static/"
STATIC_ROOT = BASE_DIR / "staticfiles"

# ── Logging (structured JSON, PHI-scrubbed) ───────────────────────────────
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "filters": {
        "phi_scrub": {"()": "apps.common.logging.PHIScrubFilter"},
    },
    "formatters": {
        "json": {"()": "apps.common.logging.JsonFormatter"},
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "json",
            "filters": ["phi_scrub"],
        },
    },
    "root": {"handlers": ["console"], "level": env("LOG_LEVEL")},
}
