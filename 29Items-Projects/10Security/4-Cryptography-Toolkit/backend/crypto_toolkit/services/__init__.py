"""Service layer — framework-free crypto/domain logic.

Rule: no Flask imports in the crypto services (aes/rsa/ecdsa/sha3/argon2/
tls_demo/attacks/kdf). Takes/returns plain data so the layer is
unit-testable in isolation. auth/audit/lesson services are Flask-aware by
necessity (request context, sessions) and live here too, clearly separated.
"""
