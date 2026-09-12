"""Uvicorn entrypoint: `python -m api_gateway.asgi` or `uvicorn api_gateway.asgi:app`."""

from __future__ import annotations

from api_gateway.main import app  # noqa: F401


def main() -> None:  # pragma: no cover - process entrypoint
    import uvicorn

    from trading_common.utils import load_settings

    settings = load_settings("api-gateway")
    uvicorn.run(
        "api_gateway.asgi:app",
        host=settings.extra.get("api_host", "0.0.0.0"),
        port=int(settings.extra.get("api_port", 8000)),
        reload=settings.env == "dev",
    )


if __name__ == "__main__":  # pragma: no cover
    main()
