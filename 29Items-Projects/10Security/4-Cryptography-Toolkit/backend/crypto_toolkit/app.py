"""Flask application factory."""

import logging

from flask import Flask, jsonify

from crypto_toolkit.config import get_config
from crypto_toolkit.extensions import db, limiter


def _configure_logging(app: Flask) -> None:
    """Structured JSON-ish lines; DEBUG only in development."""
    level = logging.DEBUG if app.config["DEBUG"] else logging.INFO
    logging.basicConfig(
        level=level,
        format='{"ts":"%(asctime)s","level":"%(levelname)s","logger":"%(name)s","msg":%(message)r}',
    )


def create_app(env: str | None = None) -> Flask:
    app = Flask(__name__)
    app.config.from_object(get_config(env))

    _configure_logging(app)

    # --- extensions ---
    db.init_app(app)
    limiter.init_app(app)

    # --- blueprints ---
    from crypto_toolkit.api import register_blueprints

    register_blueprints(app)

    # --- error envelope + logging filters ---
    from crypto_toolkit.middleware.error_handlers import register_error_handlers

    register_error_handlers(app)

    # --- health probe (used by CI smoke tests and deploy gating) ---
    @app.get("/api/health")
    def health():
        return jsonify(data={"status": "ok", "service": "crypto-toolkit"})

    with app.app_context():
        # v1 parity with migrations/001_init.sql (idempotent; production runs
        # the SQL migration pre-step — see scripts/run_migrations.py)
        db.create_all()
        if app.config["SEED_LESSONS"]:
            from crypto_toolkit.services.seed import seed_if_empty

            seed_if_empty()

    return app
