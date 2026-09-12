"""Uniform JSON error envelope: {"error": {"code", "message", "detail?"}}.

Stack traces are never leaked in production; a correlation id is attached
so support can find the matching log line.
"""

import logging
import uuid

from flask import Flask, current_app, jsonify
from werkzeug.exceptions import HTTPException

from crypto_toolkit.errors import CryptoServiceError

log = logging.getLogger(__name__)


def register_error_handlers(app: Flask) -> None:
    @app.errorhandler(CryptoServiceError)
    def handle_service_error(err: CryptoServiceError):
        correlation_id = str(uuid.uuid4())
        log.warning("service_error code=%s corr=%s detail=%s", err.code, correlation_id, err)
        return (
            jsonify(error={"code": err.code, "message": str(err), "correlation_id": correlation_id}),
            err.http_status,
        )

    @app.errorhandler(HTTPException)
    def handle_http_error(err: HTTPException):
        # Covers werkzeug's 404/405/413 and flask-limiter's 429
        code = err.name.lower().replace(" ", "_")
        return jsonify(error={"code": code, "message": err.description}), err.code

    @app.errorhandler(Exception)
    def handle_unexpected(err: Exception):
        correlation_id = str(uuid.uuid4())
        log.exception("unhandled_error corr=%s", correlation_id)
        message = repr(err) if current_app.config["DEBUG"] else "Internal server error"
        body = {"code": "internal_error", "message": message, "correlation_id": correlation_id}
        return jsonify(error=body), 500
