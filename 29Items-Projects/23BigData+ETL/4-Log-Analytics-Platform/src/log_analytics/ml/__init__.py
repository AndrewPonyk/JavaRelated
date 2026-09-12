"""ML anomaly detection: feature engineering, Isolation Forest wrapper, model registry, training.

Pure Python + pandas/sklearn — no Spark imports. The streaming job calls into this package,
and so does offline training: one feature implementation, zero train/serve skew.
"""
