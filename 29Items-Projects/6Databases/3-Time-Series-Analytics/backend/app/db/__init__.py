"""Data-plane client lifecycles.

Each module (cassandra / redis / influx) exposes:
    async connect() / async close()  — called from the app lifespan and workers
    get_*()                          — raises BackendUnavailableError when down

Clients import their drivers lazily so the app stays importable (tests, tooling)
without every driver installed.
"""


class BackendUnavailableError(RuntimeError):
    """A required data backend is not connected; mapped to HTTP 503 in main.py."""
