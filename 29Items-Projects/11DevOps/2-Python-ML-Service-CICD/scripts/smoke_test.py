"""Smoke test for the fraud-detection API.

Hits a running instance (e.g. via ``kubectl port-forward`` during a blue-green
switch) and verifies:

1. ``GET /health/ready`` returns 200.
2. ``POST /api/v1/predictions`` with a sample transaction returns 200 and a
   ``fraud_probability`` in ``[0, 1]``.

Exits 0 on success, 1 on any failure.

Usage:
    python scripts/smoke_test.py --base-url http://127.0.0.1:18000
"""

from __future__ import annotations

import argparse
import sys
import uuid

import httpx

API_PREFIX = "/api/v1"


def build_sample_payload() -> dict:
    """Build a deterministic-ish sample prediction request.

    Returns:
        A JSON-serializable payload matching the PredictionRequest schema.
    """
    return {
        "transaction_id": f"smoke-{uuid.uuid4()}",
        "account_id": "smoke-account-001",
        "amount": 129.99,
        "merchant_category": "electronics",
        "features": {
            "transaction_hour": 14,
            "days_since_last_transaction": 2.5,
            "avg_amount_30d": 87.10,
            "num_transactions_24h": 3,
        },
    }


def check_readiness(client: httpx.Client) -> bool:
    """Check that /health/ready returns 200."""
    response = client.get("/health/ready")
    if response.status_code != 200:
        print(f"FAIL: GET /health/ready returned {response.status_code}: {response.text[:200]}")
        return False
    print("PASS: GET /health/ready -> 200")
    return True


def check_prediction(client: httpx.Client) -> bool:
    """Check that a sample prediction returns 200 with a valid fraud_probability."""
    payload = build_sample_payload()
    response = client.post(f"{API_PREFIX}/predictions", json=payload)
    if response.status_code != 200:
        print(
            f"FAIL: POST {API_PREFIX}/predictions returned "
            f"{response.status_code}: {response.text[:200]}"
        )
        return False

    body = response.json()
    probability = body.get("fraud_probability")
    if not isinstance(probability, int | float) or not 0.0 <= float(probability) <= 1.0:
        print(f"FAIL: fraud_probability out of range or missing: {probability!r}")
        return False

    print(f"PASS: POST {API_PREFIX}/predictions -> 200 (fraud_probability={probability})")
    return True


def main() -> int:
    """Run all smoke checks against the given base URL.

    Returns:
        Process exit code: 0 if every check passed, 1 otherwise.
    """
    parser = argparse.ArgumentParser(description="Smoke test the fraud-detection API.")
    parser.add_argument(
        "--base-url",
        required=True,
        help="Base URL of the API instance, e.g. http://127.0.0.1:18000",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=10.0,
        help="Per-request timeout in seconds (default: 10).",
    )
    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")

    print(f"Smoke-testing {base_url} ...")
    try:
        with httpx.Client(base_url=base_url, timeout=args.timeout) as client:
            passed = check_readiness(client) and check_prediction(client)
    except httpx.HTTPError as exc:
        print(f"FAIL: HTTP error talking to {base_url}: {exc}")
        return 1

    if passed:
        print("Smoke test PASSED.")
        return 0
    print("Smoke test FAILED.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
