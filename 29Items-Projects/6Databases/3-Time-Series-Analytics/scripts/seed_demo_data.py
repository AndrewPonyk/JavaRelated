"""Demo traffic generator: registers simulated devices, then streams
random-walk metrics with occasional spikes for the anomaly pipeline to find.

Usage:
    python scripts/seed_demo_data.py [--api http://localhost:8000]
                                     [--devices 3] [--interval 5]

Flow:
  1. logs in as the bootstrap admin (ADMIN_USERNAME/ADMIN_PASSWORD env,
     defaults matching .env.example),
  2. registers missing "Sim sensor N" devices, capturing their one-time
     per-device API keys,
  3. posts one batch per device per tick using that device's own key.

Devices that already exist from a previous run (whose keys we no longer have)
fall back to the shared gateway key (DEVICE_API_KEY env).
"""

from __future__ import annotations

import argparse
import os
import random
import sys
import time
from datetime import UTC, datetime

import httpx

METRICS = {"temperature": 21.0, "humidity": 45.0, "power_w": 120.0}
SPIKE_CHANCE = 0.02  # ~2% of posts contain an obvious outlier
SITE = "sim-lab"


def login(client: httpx.Client) -> str:
    username = os.environ.get("ADMIN_USERNAME", "admin")
    password = os.environ.get("ADMIN_PASSWORD", "changeme-admin")
    resp = client.post(
        "/api/v1/auth/token", json={"username": username, "password": password}
    )
    if resp.status_code != 200:
        sys.exit(
            f"admin login failed ({resp.status_code}): {resp.text}\n"
            "Set ADMIN_USERNAME/ADMIN_PASSWORD to match the API's bootstrap admin."
        )
    return resp.json()["access_token"]


def ensure_devices(client: httpx.Client, token: str, count: int) -> dict[str, str]:
    """Return {device_id: api_key} for `count` sim devices (registering missing)."""
    headers = {"Authorization": f"Bearer {token}"}
    existing = {
        d["name"]: d["device_id"]
        for d in client.get("/api/v1/devices", headers=headers).raise_for_status().json()
        if d["site"] == SITE
    }

    gateway_key = os.environ.get("DEVICE_API_KEY", "changeme-dev-only-shared-key")
    keys: dict[str, str] = {}
    for i in range(count):
        name = f"Sim sensor {i}"
        if name in existing:
            keys[existing[name]] = gateway_key  # key unrecoverable → gateway
            continue
        resp = client.post(
            "/api/v1/devices",
            json={"name": name, "site": SITE, "device_type": "simulator"},
            headers=headers,
        )
        resp.raise_for_status()
        created = resp.json()
        keys[created["device_id"]] = created["api_key"]
        print(f"registered {created['device_id']} ({name})")
    return keys


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", default=os.environ.get("SEED_API", "http://localhost:8000"))
    parser.add_argument("--devices", type=int, default=3)
    parser.add_argument("--interval", type=float, default=5.0, help="seconds between batches")
    args = parser.parse_args()

    with httpx.Client(base_url=args.api, timeout=10) as client:
        token = login(client)
        device_keys = ensure_devices(client, token, args.devices)
        state = {device_id: dict(METRICS) for device_id in device_keys}

        # ASCII-only output: Windows consoles/pipes often default to cp1252.
        print(
            f"Seeding {len(device_keys)} devices -> {args.api} "
            f"every {args.interval}s (Ctrl+C to stop)"
        )
        while True:
            now = datetime.now(UTC).isoformat()
            for device_id, metrics in state.items():
                points = []
                for metric, value in metrics.items():
                    drift = random.gauss(0, value * 0.01)  # gentle random walk
                    metrics[metric] = value + drift
                    reading = metrics[metric]
                    if random.random() < SPIKE_CHANCE:
                        reading *= random.choice([3.0, 0.2])  # inject an anomaly
                    points.append(
                        {
                            "device_id": device_id,
                            "metric": metric,
                            "ts": now,
                            "value": round(reading, 3),
                            "tags": {"source": "seed"},
                        }
                    )
                try:
                    resp = client.post(
                        "/api/v1/ingest",
                        json={"points": points},
                        headers={"X-API-Key": device_keys[device_id]},
                    )
                    body = resp.json() if resp.status_code == 202 else resp.text
                    print(f"{now}  {device_id}  {resp.status_code}  {body}")
                except httpx.HTTPError as exc:
                    print(f"{now}  {device_id}  POST failed: {exc}")
            time.sleep(args.interval)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nstopped.")
