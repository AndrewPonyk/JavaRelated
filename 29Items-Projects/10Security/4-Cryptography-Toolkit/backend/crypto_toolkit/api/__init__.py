"""Blueprint registration. The API layer stays thin: validate → service → shape."""

from flask import Flask


def register_blueprints(app: Flask) -> None:
    from crypto_toolkit.api.routes_attacks import bp as attacks_bp
    from crypto_toolkit.api.routes_auth import bp as auth_bp
    from crypto_toolkit.api.routes_crypto import bp as crypto_bp
    from crypto_toolkit.api.routes_lessons import bp as lessons_bp
    from crypto_toolkit.api.routes_meta import bp as meta_bp
    from crypto_toolkit.api.routes_tls import bp as tls_bp

    app.register_blueprint(crypto_bp, url_prefix="/api")
    app.register_blueprint(attacks_bp, url_prefix="/api/attacks")
    app.register_blueprint(auth_bp, url_prefix="/api/auth")
    app.register_blueprint(lessons_bp, url_prefix="/api/lessons")
    app.register_blueprint(tls_bp, url_prefix="/api/tls13")
    app.register_blueprint(meta_bp, url_prefix="/api")
