# Redis and model cold-start procedure

Redis is not authoritative for stock. Forecasting keeps a validated last-known-good model in process and rejects corrupted artifacts.

1. Verify inventory REST and GraphQL operations remain healthy while Redis is unavailable.
2. Restore Redis or create a managed instance and update `REDIS_URL` through the secret manager.
3. Call `POST /v1/models/train` on the internal forecast service using reviewed history and feature version `demand-v1`.
4. Verify `GET /v1/models/active` reports the expected version/checksum and run a known forecast request.
5. Restart one forecast pod to prove the artifact loads from Redis, then roll the remaining pods.
6. Trigger an item forecast refresh and verify a new MySQL snapshot.

If no model exists, inference returns a service error; stock commands remain available. Never insert executable serialized artifacts into Redis.
