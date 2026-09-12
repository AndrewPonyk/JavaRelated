"""SSE anomaly stream: frame format, teardown guarantee, auth, availability.

The generator is driven directly (never through an ASGI byte stream — an
infinite server generator cannot be cancelled reliably under TestClient);
endpoint-level tests cover the non-streaming responses.
"""

from app.api.v1.events import event_stream


class FakePubSub:
    def __init__(self, messages: list[str]):
        self._messages = list(messages)
        self.unsubscribed_from: str | None = None
        self.closed = False

    async def get_message(self, ignore_subscribe_messages=True, timeout=None):
        if self._messages:
            return {"type": "message", "data": self._messages.pop(0)}
        return None  # what redis returns when `timeout` elapses quietly

    async def unsubscribe(self, channel: str):
        self.unsubscribed_from = channel

    async def aclose(self):
        self.closed = True


async def test_stream_yields_data_frames_then_keepalives():
    pubsub = FakePubSub(['{"metric":"temperature","score":6.7}', '{"metric":"power_w"}'])
    stream = event_stream(pubsub, "anomalies:dev-1")

    assert await anext(stream) == 'data: {"metric":"temperature","score":6.7}\n\n'
    assert await anext(stream) == 'data: {"metric":"power_w"}\n\n'
    assert await anext(stream) == ": keep-alive\n\n"  # queue drained

    await stream.aclose()


async def test_stream_tears_down_subscription_on_disconnect():
    pubsub = FakePubSub(['{"metric":"temperature"}'])
    stream = event_stream(pubsub, "anomalies:dev-1")
    await anext(stream)

    await stream.aclose()  # client went away mid-stream

    assert pubsub.unsubscribed_from == "anomalies:dev-1"
    assert pubsub.closed


async def test_stream_teardown_survives_pubsub_errors():
    class BrokenTeardown(FakePubSub):
        async def unsubscribe(self, channel: str):
            raise RuntimeError("redis went away first")

    stream = event_stream(BrokenTeardown(["x"]), "anomalies:dev-1")
    await anext(stream)
    await stream.aclose()  # must not raise


def test_endpoint_requires_auth(client):
    resp = client.get("/api/v1/events/anomalies", params={"device_id": "dev-1"})
    assert resp.status_code == 401


def test_endpoint_is_503_when_redis_is_down(client, viewer_headers):
    resp = client.get(
        "/api/v1/events/anomalies", params={"device_id": "dev-1"}, headers=viewer_headers
    )
    assert resp.status_code == 503
    assert resp.json()["error"]["code"] == "backend_unavailable"


def test_endpoint_requires_device_id(client, viewer_headers):
    resp = client.get("/api/v1/events/anomalies", headers=viewer_headers)
    assert resp.status_code == 422
