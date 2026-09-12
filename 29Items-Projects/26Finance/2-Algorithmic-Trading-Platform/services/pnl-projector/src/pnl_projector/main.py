"""pnl-projector entrypoint."""

from __future__ import annotations

import asyncio
import contextlib
import signal as os_signal

from pnl_projector.projector_service import ProjectorService
from trading_common.db import make_engine, make_session_factory
from trading_common.messaging import make_bus
from trading_common.portfolio import InMemoryPositionStore, RedisPositionStore
from trading_common.utils import configure_logging, load_settings


async def main() -> None:  # pragma: no cover - process entrypoint
    settings = load_settings("pnl-projector")
    configure_logging(settings.service_name, settings.env, settings.log_level)

    bus = make_bus(settings.kafka_bootstrap_servers, group_id="pnl-projector")
    await bus.start()

    engine = make_engine(settings.database_url)
    session_factory = make_session_factory(engine)
    store = (
        RedisPositionStore(settings.redis_url)
        if settings.redis_url and not settings.redis_url.startswith("redis://localhost")
        else InMemoryPositionStore()
    )

    service = ProjectorService(bus, store, session_factory)

    loop = asyncio.get_running_loop()
    stop = asyncio.Event()
    for sig in (os_signal.SIGINT, os_signal.SIGTERM):
        with contextlib.suppress(NotImplementedError):
            loop.add_signal_handler(sig, stop.set)

    run_task = asyncio.ensure_future(service.run())
    await stop.wait()
    await bus.stop()
    with contextlib.suppress(asyncio.CancelledError):
        await run_task
    await engine.dispose()


if __name__ == "__main__":  # pragma: no cover
    asyncio.run(main())
