"""Central configuration.

Resolution order (highest wins):
    st.secrets (Streamlit Cloud)  >  environment variables  >  .env file  >  defaults

``demo_mode`` is derived, not configured: no DATABASE_URL -> the app runs fully
in-memory on bundled sample data (a supported deployment shape, see
docs/ARCHITECTURE.md §2.1).
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = Field(default="development", description="development | staging | production")
    log_level: str = "INFO"
    database_url: str | None = Field(
        default=None,
        description="e.g. postgresql+psycopg2://user:pass@host:5432/db?sslmode=require",
    )
    max_upload_mb: int = 25
    default_alpha: float = 0.05
    #: Frames larger than this are analyzed on a seeded sample (a visible banner says so).
    max_analysis_rows: int = 200_000
    #: Optional error-tracking DSN; leave unset to disable Sentry entirely.
    sentry_dsn: str | None = None

    @property
    def demo_mode(self) -> bool:
        """No database configured -> in-memory demo mode; nothing is persisted."""
        return self.database_url is None


def _streamlit_secrets() -> dict[str, object]:
    """Flat scalar keys from st.secrets, lowercased to match Settings field names.

    The import stays inside the function on purpose: everything below the UI layer
    must work without Streamlit installed (tests, future service extraction).
    """
    try:
        import streamlit as st

        return {
            key.lower(): value
            for key, value in st.secrets.items()
            if isinstance(value, str | int | float | bool)
        }
    except Exception:
        # Not running under Streamlit, or no secrets file present.
        return {}


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    # Init kwargs take highest priority in pydantic-settings — st.secrets wins on Cloud.
    return Settings(**_streamlit_secrets())  # type: ignore[arg-type]
