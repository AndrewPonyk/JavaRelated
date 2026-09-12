"""Configuration classes. All values overridable via environment variables.

Rule: defaults are SAFE — a missing env var falls back to the strictest
setting, never the loosest (see docs/TECH-NOTES.md §3.4).
"""

import os


class BaseConfig:
    """Safe defaults shared by every environment."""

    SECRET_KEY = os.environ.get("SECRET_KEY", "")  # empty -> app refuses to boot in prod
    SQLALCHEMY_TRACK_MODIFICATIONS = False
    SQLALCHEMY_DATABASE_URI = os.environ.get("DATABASE_URL", "sqlite:///crypto_toolkit.db")

    # Strictest-possible fallbacks
    RATELIMIT_DEFAULT = os.environ.get("RATELIMIT_DEFAULT", "30 per minute")
    MAX_CONTENT_LENGTH = int(os.environ.get("MAX_PAYLOAD_KB", "64")) * 1024
    PROPAGATE_EXCEPTIONS = True

    # --- auth & audit ---
    TOKEN_TTL_HOURS = int(os.environ.get("TOKEN_TTL_HOURS", "12"))
    AUDIT_IP_SALT = os.environ.get("AUDIT_IP_SALT", "")  # falls back to SECRET_KEY hash
    SEED_LESSONS = os.environ.get("SEED_LESSONS", "true").lower() in ("1", "true", "yes")


class DevelopmentConfig(BaseConfig):
    DEBUG = True
    SECRET_KEY = os.environ.get("SECRET_KEY", "dev-only-insecure-key")


class TestingConfig(BaseConfig):
    TESTING = True
    SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"
    RATELIMIT_DEFAULT = "1000 per minute"  # don't fight the limiter in tests
    RATELIMIT_ENABLED = False  # per-route limits off too — auth tests register a lot
    SECRET_KEY = "test-key"  # noqa: S105 — throwaway fixture value, never a real secret
    SEED_LESSONS = False  # deterministic lesson CRUD tests


class StagingConfig(BaseConfig):
    DEBUG = False


class ProductionConfig(BaseConfig):
    DEBUG = False


_BY_ENV = {
    "development": DevelopmentConfig,
    "testing": TestingConfig,
    "staging": StagingConfig,
    "production": ProductionConfig,
}


def get_config(env: str | None = None) -> type[BaseConfig]:
    env = env or os.environ.get("FLASK_ENV", "development")
    try:
        cfg = _BY_ENV[env]
    except KeyError:
        raise ValueError(f"Unknown FLASK_ENV {env!r}; expected one of {sorted(_BY_ENV)}") from None
    if env == "production" and not cfg.SECRET_KEY:
        raise RuntimeError("SECRET_KEY must be set in production")
    return cfg
