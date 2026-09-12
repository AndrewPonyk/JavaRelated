"""Shared, dependency-light building blocks: config, contracts, parsing, logging, Kafka helpers.

Everything in this package must stay importable without Spark, OpenSearch, or Kafka client
libraries at import time — it is reused by streaming jobs, services, and offline training.
"""
