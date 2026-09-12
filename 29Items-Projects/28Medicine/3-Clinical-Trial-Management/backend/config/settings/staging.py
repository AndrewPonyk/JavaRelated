"""Staging settings — production-like, with looser diagnostics.

Inherits the hardened production configuration but may relax a few knobs (e.g.
verbose logging) to aid pre-prod debugging. Deploys the SAME image as prod.
"""
from __future__ import annotations

from .production import *  # noqa: F401,F403

LOG_LEVEL = "DEBUG"
# TODO: point at staging IdP realm, staging S3 bucket, and a non-prod KMS key.
