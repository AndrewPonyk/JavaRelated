from collections.abc import Generator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.exc import OperationalError

from app.core.config import get_settings
from app.main import app


@pytest.fixture(scope="session")
def migrated_database() -> Generator[None, None, None]:
    settings = get_settings()
    engine = create_engine(settings.database_url, connect_args={"connect_timeout": 2}, future=True)
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
    except OperationalError as exc:
        pytest.skip(f"PostGIS test database is unavailable: {exc}")

    migration = Path(__file__).resolve().parents[2] / "database" / "migrations" / "001_init_postgis.sql"
    with engine.begin() as connection:
        raw = connection.connection.driver_connection
        with raw.cursor() as cursor:
            cursor.execute(migration.read_text(encoding="utf-8"))
    yield
    engine.dispose()


@pytest.fixture
def clean_database(migrated_database: None) -> Generator[None, None, None]:
    settings = get_settings()
    engine = create_engine(settings.database_url, connect_args={"connect_timeout": 2}, future=True)
    with engine.begin() as connection:
        connection.execute(text("TRUNCATE user_roles, users, layers, ml_classification_jobs, dataset_features, datasets RESTART IDENTITY CASCADE"))
        connection.execute(text("INSERT INTO roles (name, description) VALUES ('admin', 'Platform administrator'), ('analyst', 'GIS analyst') ON CONFLICT (name) DO NOTHING"))
    yield
    engine.dispose()


@pytest.fixture
def client(clean_database: None) -> TestClient:
    return TestClient(app)


@pytest.fixture
def no_db_client() -> TestClient:
    return TestClient(app)
