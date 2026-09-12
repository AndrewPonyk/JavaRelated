"""Spark Structured Streaming layer.

Deliberately thin: parsing/normalization logic lives in `log_analytics.common`,
feature/scoring logic in `log_analytics.ml` — these modules only wire DataFrames.
Requires pyspark (requirements-spark.txt) — not imported by services or unit tests.
"""
