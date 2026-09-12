"""Run the paper-trading app standalone: `python -m orchestrator.main`.

Persists positions to the configured database so the api-gateway can serve them.
"""

from __future__ import annotations

import asyncio

from orchestrator.app import PaperTradingApp
from trading_common.db import create_all, make_engine, make_session_factory
from trading_common.utils import configure_logging, load_settings


async def main() -> None:  # pragma: no cover - process entrypoint
    settings = load_settings("orchestrator")
    configure_logging(settings.service_name, settings.env, settings.log_level)

    engine = make_engine(settings.database_url)
    if settings.database_url.startswith("sqlite"):
        await create_all(engine)
    session_factory = make_session_factory(engine)

    # Reuse one app/store across repeated sessions so positions accumulate and the
    # dashboard keeps updating. Each session drives a batch of synthetic ticks.
    app = PaperTradingApp(session_factory=session_factory)
    sessions = int(settings.extra.get("paper_sessions", 50))
    try:
        for _ in range(sessions):
            await app.run(max_ticks=500)
    finally:
        await engine.dispose()


if __name__ == "__main__":  # pragma: no cover
    asyncio.run(main())
